package se.kth.extractor;

import lombok.Getter;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.lang.annotation.Annotation;
import java.util.Set;

@Getter
public class CustomScanner extends CtScanner {

    private final Set<CtElement> executedElements;

    public CustomScanner(Set<CtElement> ctElements) {
        this.executedElements = ctElements;
    }

    public void collectExecutedElements(CtElement ctElement) {
        if (ctElement == null || this.executedElements.contains(ctElement)) {
            return;
        }
        this.executedElements.add(ctElement);

        ctElement.accept(this);
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        this.collectExecutedElements(invocation.getExecutable());
        super.visitCtInvocation(invocation);
    }

    @Override
    public <T> void visitCtConstructorCall(CtConstructorCall<T> constructorCall) {
        this.collectExecutedElements(constructorCall.getExecutable());
        super.visitCtConstructorCall(constructorCall);
    }

    @Override
    public void visitCtIf(CtIf ifElement) {
        this.collectExecutedElements(ifElement.getThenStatement());
        if (ifElement.getElseStatement() != null) {
            this.collectExecutedElements(ifElement.getElseStatement());
        }
        super.visitCtIf(ifElement);
    }

    @Override
    public void visitCtWhile(CtWhile whileLoop) {
        this.collectExecutedElements(whileLoop.getLoopingExpression());
        this.collectExecutedElements(whileLoop.getBody());
        super.visitCtWhile(whileLoop);
    }

    @Override
    public <R> void visitCtBlock(CtBlock<R> block) {
        for (CtStatement statement : block.getStatements()) {
            this.collectExecutedElements(statement);
        }
        super.visitCtBlock(block);
    }

    @Override
    public <T> void visitCtMethod(CtMethod<T> method) {
        this.collectExecutedElements(method.getBody());
        super.visitCtMethod(method);
    }

    @Override
    public <T> void visitCtExecutableReference(CtExecutableReference<T> reference) {
        this.collectExecutedElements(reference.getDeclaration());
        super.visitCtExecutableReference(reference);
    }

    @Override
    public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
        this.collectExecutedElements(reference.getDeclaration());
        super.visitCtTypeReference(reference);
    }

    @Override
    public <A extends Annotation> void visitCtAnnotation(CtAnnotation<A> annotation) {
        this.collectExecutedElements(annotation.getAnnotationType());
        super.visitCtAnnotation(annotation);
    }

    @Override
    public <T> void visitCtFieldReference(CtFieldReference<T> reference) {
        this.collectExecutedElements(reference.getDeclaringType());
        super.visitCtFieldReference(reference);
    }

    @Override
    public void visitCtTry(CtTry tryBlock) {
        this.collectExecutedElements(tryBlock.getBody());
        super.visitCtTry(tryBlock);
    }

    @Override
    public void visitCtCatch(CtCatch catchBlock) {
        this.collectExecutedElements(catchBlock.getBody());
        super.visitCtCatch(catchBlock);
    }

    @Override
    public void visitCtTryWithResource(CtTryWithResource tryWithResource) {
        this.collectExecutedElements(tryWithResource.getBody());
        super.visitCtTryWithResource(tryWithResource);
    }

    @Override
    public <T> void visitCtClass(CtClass<T> ctClass) {
        ctClass.getFields().stream()
                .filter(CtModifiable::isStatic)
                .forEach(this::collectExecutedElements);
        ctClass.getAnnotations()
                .forEach(this::collectExecutedElements);
        super.visitCtClass(ctClass);
    }

    @Override
    public <T, A extends T> void visitCtAssignment(CtAssignment<T, A> assignment) {
        this.collectExecutedElements(assignment.getAssigned());
        this.collectExecutedElements(assignment.getAssignment());
        super.visitCtAssignment(assignment);
    }

    @Override
    public <R> void visitCtReturn(CtReturn<R> returnStatement) {
        this.collectExecutedElements(returnStatement.getReturnedExpression());
        super.visitCtReturn(returnStatement);
    }

    @Override
    public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
        this.collectExecutedElements(fieldRead.getVariable());
        super.visitCtFieldRead(fieldRead);
    }

}
