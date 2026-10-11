/*
 * 借用检查语义 pass（3e 最小借用检查）。
 *
 * 做什么：在 AST 构建完成后、codegen 前对每个函数体做两遍遍历：
 *   Pass A 收集每个借用的「活跃区间」——区间左端为创建语句，右端为最后一次使用
 *   （NLL 简化版，区间 [create, lastUse] 闭区间；离开块或末次使用后借用自动释放）；
 *   Pass B 按区间与变量所有权状态做三类检查并抛 rustjerror(line, msg)：
 *     · move：i32/bool 标量赋值/传参为 Copy（不移动）；结构体赋值/传参为 move，
 *       move 后原变量再读报 use-after-move；
 *     · 借用冲突：同一变量仅允许一个可变借用或多个只读借用，可变/只读混用报错，
 *       冲突判定 = 创建借用时该变量已有活跃区间覆盖当前语句；
 *     · 解引用：`*r` 要求 r 是已登记的引用变量（borrow 绑定），否则报「解引用非引用」。
 *   字段级借用视为对基变量的整体借用（p.f 整体借用 p）；不做生命周期标注、引用字段、
 *   跨函数引用参数/返回值、Copy/Clone 实现、&self 方法签名与泛型实例化。
 * 提供什么功能：
 *   - check(List<function> fns)：对所有函数体依次做两遍遍历。
 */
package frontend;

import ast.binop;
import ast.block;
import ast.borrow;
import ast.call;
import ast.deref;
import ast.expr;
import ast.field;
import ast.fieldassignstmt;
import ast.forstmt;
import ast.function;
import ast.ident;
import ast.ifstmt;
import ast.intlit;
import ast.letstmt;
import ast.assignstmt;
import ast.returnstmt;
import ast.stmt;
import ast.structlit;
import ast.unop;
import ast.whilestmt;
import error.rustjerror;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class borrows {
    /* 借用记录：target 为被借变量栈帧偏移，isMut 为可变/只读，区间 [create, lastUse]。 */
    private static final class brec {
        final int id, target, create;
        final boolean isMut;
        int lastUse;

        brec(int i, int t, boolean m, int c) {
            id = i;
            target = t;
            isMut = m;
            create = c;
            lastUse = c;
        }
    }

    /* 变量所有权状态：moved=true 表示该结构体变量已被 move 走，再读即报 use-after-move。 */
    private static final class bvar {
        final String name;
        final boolean isStruct;
        boolean moved;

        bvar(String n, boolean s) {
            name = n;
            isStruct = s;
        }
    }

    private final List<brec> recs = new ArrayList<>();
    private final Map<Integer, Integer> refOf = new HashMap<>();
    private int stmtNo = 0;
    private int nextId = 1;

    /* 入口：对每个函数体做 Pass A（借用区间）与 Pass B（检查），两遍计数互不影响。 */
    public static void check(List<function> fns) {
        for (function f : fns) {
            borrows b = new borrows();
            b.walkA(f.body);
            b.stmtNo = 0;
            b.walkB(f.body, new HashMap<>());
        }
    }

    /* Pass A：顺序遍历语句，登记借用记录并跟踪引用变量 → 借用 id 的映射（refOf）。 */
    private void walkA(block b) {
        for (stmt s : b.stmts) {
            stmtNo++;
            walkAStmt(s);
        }
        if (b.tail != null) {
            stmtNo++;
            walkAExpr(b.tail);
        }
    }

    private void walkAStmt(stmt s) {
        if (s instanceof letstmt l) {
            if (l.init instanceof borrow bb) {
                refOf.put(l.offset, addRec(bb));
                walkAExpr(bb.operand);
            } else walkAExpr(l.init);
        } else if (s instanceof assignstmt a) {
            if (refOf.containsKey(a.offset)) endRef(a.offset);
            walkAExpr(a.value);
        } else if (s instanceof fieldassignstmt fa) {
            walkAExpr(fa.value);
        } else if (s instanceof returnstmt r) {
            if (r.value != null) walkAExpr(r.value);
        } else if (s instanceof ifstmt i) {
            walkAExpr(i.cond);
            walkA(i.then);
            if (i.els != null) walkA(i.els);
        } else if (s instanceof whilestmt w) {
            walkAExpr(w.cond);
            walkA(w.body);
        } else if (s instanceof forstmt f) {
            walkAExpr(f.lo);
            walkAExpr(f.hi);
            walkA(f.body);
        }
    }

    private void walkAExpr(expr e) {
        if (e instanceof borrow b) {
            int id = addRec(b);
            use(id);
            walkAExpr(b.operand);
        } else if (e instanceof deref d) {
            if (d.operand instanceof ident v) {
                Integer id = refOf.get(v.offset);
                if (id != null) use(id);
            } else walkAExpr(d.operand);
        } else if (e instanceof call c) {
            for (expr a : c.args) walkAExpr(a);
        } else if (e instanceof binop b) {
            walkAExpr(b.left);
            walkAExpr(b.right);
        } else if (e instanceof unop u) {
            walkAExpr(u.operand);
        } else if (e instanceof structlit g) {
            for (expr a : g.args) walkAExpr(a);
        }
    }

    /* 登记借用记录：borrow 目标为 ident 的变量偏移或 field 的基变量偏移（字段级视为整体借用）。 */
    private int addRec(borrow b) {
        int target = b.operand instanceof ident v ? v.offset : ((field) b.operand).base;
        int id = nextId++;
        recs.add(new brec(id, target, b.isMut, stmtNo));
        return id;
    }

    /* 把借用 id 的末次使用推进到当前语句（NLL：活跃区间的右端）。 */
    private void use(int id) {
        for (brec r : recs) if (r.id == id) {
            r.lastUse = stmtNo;
            return;
        }
    }

    /* 引用变量被重新赋值：其绑定的借用区间提前于赋值语句结束。 */
    private void endRef(int refOff) {
        Integer id = refOf.remove(refOff);
        if (id != null) for (brec r : recs) if (r.id == id) {
            r.lastUse = stmtNo;
            return;
        }
    }

    /* Pass B：与 Pass A 同步递增 stmtNo，逐语句做 move / 借用冲突 / 解引用检查。 */
    private void walkB(block b, Map<Integer, bvar> vars) {
        for (stmt s : b.stmts) {
            stmtNo++;
            walkBStmt(s, vars);
        }
        if (b.tail != null) {
            stmtNo++;
            walkBExpr(b.tail, vars);
        }
    }

    private void walkBStmt(stmt s, Map<Integer, bvar> vars) {
        if (s instanceof letstmt l) {
            boolean st = l.init instanceof structlit;
            if (l.init instanceof ident iv) {
                bvar src = vars.get(iv.offset);
                if (src != null && src.isStruct) {
                    st = true;
                    moveVar(iv.offset, iv.line, vars);   // move 即读取，不再走 walkBExpr
                } else walkBExpr(l.init, vars);
            } else walkBExpr(l.init, vars);
            vars.put(l.offset, new bvar(l.name, st));
        } else if (s instanceof assignstmt a) {
            if (a.value instanceof ident av) {
                bvar src = vars.get(av.offset);
                if (src != null && src.isStruct) moveVar(av.offset, av.line, vars);
                else walkBExpr(a.value, vars);
            } else walkBExpr(a.value, vars);
            writeVar(a.offset, a.line, vars);
        } else if (s instanceof fieldassignstmt fa) {
            if (fa.value instanceof ident fv) {
                bvar src = vars.get(fv.offset);
                if (src != null && src.isStruct) moveVar(fv.offset, fv.line, vars);
                else walkBExpr(fa.value, vars);
            } else walkBExpr(fa.value, vars);
            writeVar(fa.base, fa.line, vars);
        } else if (s instanceof returnstmt r) {
            if (r.value != null) walkBExpr(r.value, vars);
        } else if (s instanceof ifstmt i) {
            walkBExpr(i.cond, vars);
            walkB(i.then, vars);
            if (i.els != null) walkB(i.els, vars);
        } else if (s instanceof whilestmt w) {
            walkBExpr(w.cond, vars);
            walkB(w.body, vars);
        } else if (s instanceof forstmt f) {
            walkBExpr(f.lo, vars);
            walkBExpr(f.hi, vars);
            vars.put(f.offset, new bvar(f.name, false));
            walkB(f.body, vars);
        }
    }

    private void walkBExpr(expr e, Map<Integer, bvar> vars) {
        if (e instanceof borrow b) {
            if (!(b.operand instanceof ident) && !(b.operand instanceof field))
                throw new rustjerror(b.line, "借用仅支持变量或字段");
            int target = b.operand instanceof ident v ? v.offset : ((field) b.operand).base;
            checkBorrow(b, target, vars);
        } else if (e instanceof deref d) {
            if (d.operand instanceof ident v) {
                if (!refOf.containsKey(v.offset)) {
                    bvar bv = vars.get(v.offset);
                    throw new rustjerror(d.line, "解引用非引用: " + (bv != null ? bv.name : "变量"));
                }
            } else if (!(d.operand instanceof borrow)) {
                throw new rustjerror(d.line, "解引用非引用");
            }
            walkBExpr(d.operand, vars);
        } else if (e instanceof call c) {
            for (expr a : c.args) {
                if (a instanceof ident v) {
                    bvar bv = vars.get(v.offset);
                    if (bv != null && bv.isStruct) {
                        moveVar(v.offset, v.line, vars);
                        continue;
                    }
                }
                walkBExpr(a, vars);
            }
        } else if (e instanceof binop b) {
            walkBExpr(b.left, vars);
            walkBExpr(b.right, vars);
        } else if (e instanceof unop u) {
            walkBExpr(u.operand, vars);
        } else if (e instanceof structlit g) {
            for (expr a : g.args) walkBExpr(a, vars);
        } else if (e instanceof field f) {
            readVar(f.base, f.line, vars);
        } else if (e instanceof ident v) {
            readVar(v.offset, v.line, vars);
        }
    }

    /* 读变量：moved 报 use-after-move；存在活跃可变借用时禁止读取（借用冲突）。 */
    private void readVar(int off, int line, Map<Integer, bvar> vars) {
        bvar v = vars.get(off);
        if (v == null) return;
        if (v.moved) throw new rustjerror(line, "use-after-move: " + v.name + " 已被移动");
        if (activeMut(off)) throw new rustjerror(line, "借用冲突: 变量 " + v.name + " 存在活跃可变借用，不能读取");
    }

    /* 写变量：存在任一活跃借用（可变或只读）时禁止赋值（借用冲突）；写可复活 moved 变量。 */
    private void writeVar(int off, int line, Map<Integer, bvar> vars) {
        bvar v = vars.get(off);
        if (v == null) return;
        if (activeAny(off)) throw new rustjerror(line, "借用冲突: 变量 " + v.name + " 存在活跃借用，不能赋值");
        v.moved = false;
    }

    /* move 变量：存在活跃借用时禁止移动；标量（Copy）不做本调用。 */
    private void moveVar(int off, int line, Map<Integer, bvar> vars) {
        bvar v = vars.get(off);
        if (v == null) return;
        if (activeAny(off)) throw new rustjerror(line, "借用冲突: 变量 " + v.name + " 存在活跃借用，不能移动");
        v.moved = true;
    }

    /* 创建借用：moved 目标报 use-after-move；可变借用与任一活跃借用冲突、只读借用与活跃可变借用冲突。 */
    private void checkBorrow(borrow b, int target, Map<Integer, bvar> vars) {
        bvar v = vars.get(target);
        if (v == null) return;
        if (v.moved) throw new rustjerror(b.line, "use-after-move: " + v.name + " 已被移动");
        if (b.isMut) {
            if (activeAny(target)) throw new rustjerror(b.line, "借用冲突: 可变借用与已有借用冲突");
        } else if (activeMut(target)) {
            throw new rustjerror(b.line, "借用冲突: 只读借用与可变借用冲突");
        }
    }

    /* 是否存在覆盖当前语句的可变借用 / 任一借用。 */
    private boolean activeMut(int off) {
        for (brec r : recs) if (r.target == off && r.isMut && r.create <= stmtNo && stmtNo <= r.lastUse) return true;
        return false;
    }

    private boolean activeAny(int off) {
        for (brec r : recs) if (r.target == off && r.create <= stmtNo && stmtNo <= r.lastUse) return true;
        return false;
    }

}
