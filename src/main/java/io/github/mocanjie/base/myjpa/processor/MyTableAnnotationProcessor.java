package io.github.mocanjie.base.myjpa.processor;

import io.github.mocanjie.base.myjpa.annotation.MyTable;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * 编译期 APT 规范校验处理器。
 *
 * <p>强制执行 {@code @MyTable} 与 {@code MyTableEntity} 的双向绑定规则：
 * <ol>
 *   <li><b>Rule-1</b>：标注了 {@code @MyTable} 的类必须实现 {@code MyTableEntity} 接口</li>
 *   <li><b>Rule-2</b>：实现了 {@code MyTableEntity} 的具体类必须标注 {@code @MyTable}</li>
 * </ol>
 * 违反任一规则将产生 {@link Diagnostic.Kind#ERROR} 级别的编译错误，构建无法通过。
 *
 * <p>注意：抽象类和接口不受 Rule-2 约束，允许作为中间基类使用。
 */
@SupportedAnnotationTypes("*")
public class MyTableAnnotationProcessor extends AbstractProcessor {

    private static final String MY_TABLE_ENTITY_FQN = "io.github.mocanjie.base.myjpa.MyTableEntity";

    private TypeMirror myTableEntityType;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        TypeElement entityTypeElement = processingEnv.getElementUtils().getTypeElement(MY_TABLE_ENTITY_FQN);
        if (entityTypeElement != null) {
            myTableEntityType = entityTypeElement.asType();
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // 跳过最终合成轮次；MyTableEntity 未在类路径上时跳过（自身编译时的边界情况）
        if (roundEnv.processingOver() || myTableEntityType == null) {
            return false;
        }

        Types typeUtils = processingEnv.getTypeUtils();
        Messager messager = processingEnv.getMessager();

        // Rule-1：@MyTable 注解的类必须实现 MyTableEntity
        for (Element element : roundEnv.getElementsAnnotatedWith(MyTable.class)) {
            if (!(element instanceof TypeElement typeElement)) continue;
            if (!typeUtils.isAssignable(typeElement.asType(), myTableEntityType)) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "[@MyTable 规范] " + typeElement.getQualifiedName()
                                + " 标注了 @MyTable 但未实现 MyTableEntity 接口",
                        element);
            }
        }

        // Rule-2：实现 MyTableEntity 的具体类必须标注 @MyTable
        for (Element element : roundEnv.getRootElements()) {
            if (!(element instanceof TypeElement typeElement)) continue;
            // 跳过接口和抽象类（允许作为中间基类）
            if (typeElement.getKind() == ElementKind.INTERFACE) continue;
            if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) continue;
            if (!typeUtils.isAssignable(typeElement.asType(), myTableEntityType)) continue;

            if (typeElement.getAnnotation(MyTable.class) == null) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "[@MyTable 规范] " + typeElement.getQualifiedName()
                                + " 实现了 MyTableEntity 接口但未标注 @MyTable 注解",
                        element);
            }
        }

        return false;
    }
}
