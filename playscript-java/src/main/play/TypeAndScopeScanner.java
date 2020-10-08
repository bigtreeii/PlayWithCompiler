package main.play;

import org.antlr.v4.runtime.ParserRuleContext;
import main.play.PlayScriptParser.*;

import java.util.Stack;

/**
 * 第一遍扫描：创建所有scope并加入到ast中。主要包括namespace、类、函数、代码块scope
 */
public class TypeAndScopeScanner extends PlayScriptBaseListener {

    private AnnotatedTree at = null;

    private Stack<Scope> scopeStack = new Stack<Scope>();

    public TypeAndScopeScanner(AnnotatedTree at) {
        this.at = at;
    }


    /**
     *     1、将传入的<ctx,scope>放入ast树中，一个节点对应一个scope
     *     2、将传入的scope放入当前临时的stack中
     */
    private Scope pushScope(Scope scope, ParserRuleContext ctx) {
        at.node2Scope.put(ctx, scope);
        scope.ctx = ctx;

        scopeStack.push(scope);
        return scope;
    }

    /**
     *      1、从栈中弹出scope
     */
    private void popScope() {
        scopeStack.pop();
    }

    /**
     *      1、在遍历树的过程中，获取当前的Scope
     */
    private Scope currentScope() {
        if (scopeStack.size() > 0) {
            return scopeStack.peek();
        } else {
            return null;
        }
    }

    /**
     *      1、进入programe解析，创建namespace
     *      2、绑定ast的namespace并且入栈
     */
    @Override
    public void enterProg(ProgContext ctx) {
        NameSpace scope = new NameSpace("", currentScope(), ctx);
        at.nameSpace = scope; //scope的根
        pushScope(scope, ctx);
    }

    @Override
    public void exitProg(ProgContext ctx) {
        popScope();
    }


    /**
     *      1、解析代码块的时候，对于函数来说，创建一个新的blockScope，并且enclosingScope设置为函数scope，能访问函数的scope，函数scope中增加blockScope成员
     *      2、函数的scope中增加当前blockScope这个成员
     *      3、将新scope和节点加入栈中
     */
    @Override
    public void enterBlock(BlockContext ctx) {
        if (!(ctx.parent instanceof FunctionBodyContext)){
            BlockScope scope = new BlockScope(currentScope(), ctx);
            currentScope().addSymbol(scope);
            pushScope(scope, ctx);
        }
    }

    @Override
    public void exitBlock(BlockContext ctx) {
        if (!(ctx.parent instanceof FunctionBodyContext)) {
            popScope();
        }
    }


    /**
     *      1、解析statement，如果是for循环语句需要额外创建blockScope
     *      2、将blockScope加入到父节点（for）的成员列表中
     *      3、将blockScope和ctx节点入栈
     */
    @Override
    public void enterStatement(StatementContext ctx) {
        //为for建立额外的Scope
        if (ctx.FOR() != null) {
            BlockScope scope = new BlockScope(currentScope(), ctx);
            currentScope().addSymbol(scope);
            pushScope(scope, ctx);
        }
    }

    @Override
    public void exitStatement(StatementContext ctx) {
        //释放for语句的外层scope
        if (ctx.FOR() != null) {
            popScope();
        }
    }


    /**
     *      1、获取函数名称，并创建函数的scope，加入到ast的符号表中
     *      2、将此函数的scope加入到父scope的成员中
     *      3、将此函数的scope和ctx节点入栈
     *
     */
    @Override
    public void enterFunctionDeclaration(FunctionDeclarationContext ctx) {
        String idName = ctx.IDENTIFIER().getText();

        //注意：目前funtion的信息并不完整，参数要等到TypeResolver.java中去确定。
        Function function = new Function(idName, currentScope(), ctx);

        at.types.add(function);

        currentScope().addSymbol(function);

        // 创建一个新的scope
        pushScope(function, ctx);
    }

    @Override
    public void exitFunctionDeclaration(FunctionDeclarationContext ctx) {
        popScope();
    }


    /**
     *      1、解析类定义语句时，获取类名
     *      2、将类名存储到本地符号表中，如果有重名的符号就报警
     *      3、将此类定义的scope加入到父scope的成员列表中
     *      4、将此类的scope和节点ctx入栈
     *
     */
    @Override
    public void enterClassDeclaration(ClassDeclarationContext ctx) {
        // 把类的签名存到符号表中，不能跟已有符号名称冲突
        String idName = ctx.IDENTIFIER().getText();

        Class theClass = new Class(idName, ctx);
        at.types.add(theClass);

        if (at.lookupClass(currentScope(), idName) != null) {
            at.log("duplicate class name:" + idName, ctx); // 只是报警，但仍然继续解析
        }

        currentScope().addSymbol(theClass);

        // 创建一个新的scope
        pushScope(theClass, ctx);

    }

    @Override
    public void exitClassDeclaration(ClassDeclarationContext ctx) {
        popScope();
    }


}