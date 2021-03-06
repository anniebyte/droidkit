package droidkit.processor;

import com.squareup.javapoet.*;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import droidkit.annotation.InjectView;
import droidkit.annotation.OnActionClick;
import droidkit.annotation.OnClick;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Daniel Serdyukov
 */
class LifecycleMaker implements ClassMaker {

    static final ClassName MENU_ITEM = ClassName.get("android.view", "MenuItem");

    private static final String LIFECYCLE = "$Lifecycle";

    private static final String M_ON_CLICK = "mOnClick";

    private static final ClassName VIEW = ClassName.get("android.view", "View");

    private static final ClassName ON_CLICK_LISTENER = ClassName.get("android.view", "View", "OnClickListener");

    private static final ClassName WINDOW = ClassName.get("android.view", "Window");

    private static final ClassName VIEWS = ClassName.get("droidkit.view", "Views");

    private static final ClassName SPARSE_ARRAY = ClassName.get("android.util", "SparseArray");

    private static final ClassName MENU_ITEM_LISTENER = ClassName.get("android.view", "MenuItem",
            "OnMenuItemClickListener");

    private static final String M_ON_ACTION_CLICK = "mOnActionClick";

    private final Map<Element, Integer> mInjectView = new HashMap<>();

    private final Map<ExecutableElement, int[]> mOnClick = new HashMap<>();

    private final Map<ExecutableElement, int[]> mOnActionClick = new HashMap<>();

    private final JavacProcessingEnvironment mEnv;

    private TypeElement mOriginElement;

    private ClassName mOriginType;

    LifecycleMaker(ProcessingEnvironment env) {
        mEnv = (JavacProcessingEnvironment) env;
    }

    LifecycleMaker withOriginType(TypeElement originType) {
        mOriginElement = originType;
        mOriginType = ClassName.get(originType);
        return this;
    }

    void emit(Element element, InjectView annotation) {
        JavacUtils.<JCTree.JCVariableDecl>asTree(element).mods.flags &= ~Flags.PRIVATE;
        mInjectView.put(element, annotation.value());
    }

    void emit(Element element, OnClick annotation) {
        JavacUtils.<JCTree.JCMethodDecl>asTree(element).mods.flags &= ~Flags.PRIVATE;
        mOnClick.put((ExecutableElement) element, annotation.value());
    }

    void emit(Element element, OnActionClick annotation) {
        JavacUtils.<JCTree.JCMethodDecl>asTree(element).mods.flags &= ~Flags.PRIVATE;
        mOnActionClick.put((ExecutableElement) element, annotation.value());
    }

    @Override
    public JavaFile make() throws IOException {
        final TypeSpec.Builder builder = TypeSpec
                .classBuilder(mOriginType.simpleName() + LIFECYCLE)
                .addOriginatingElement(mOriginElement);
        makeFields(builder);
        makeLifecycleMethods(builder);
        final TypeSpec spec = builder.build();
        final JavaFile javaFile = JavaFile.builder(mOriginType.packageName(), spec)
                .addFileComment(AUTO_GENERATED)
                .build();
        final JavaFileObject sourceFile = mEnv.getFiler().createSourceFile(javaFile.packageName + "."
                + spec.name, mOriginElement);
        try (final Writer writer = new BufferedWriter(sourceFile.openWriter())) {
            javaFile.writeTo(writer);
        }
        return javaFile;
    }

    private void makeFields(TypeSpec.Builder builder) {
        builder.addField(FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(Map.class), VIEW, ON_CLICK_LISTENER),
                M_ON_CLICK, Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", ClassName.get(WeakHashMap.class))
                .build());
        builder.addField(FieldSpec.builder(ParameterizedTypeName.get(SPARSE_ARRAY, MENU_ITEM_LISTENER),
                M_ON_ACTION_CLICK, Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T<>()", SPARSE_ARRAY)
                .build());
    }

    private void makeLifecycleMethods(TypeSpec.Builder builder) {
        makeInjectViews(builder);
        makeOnCreate(builder);
        makeOnResume(builder);
        makeOnPause(builder);
        makeOnDestroy(builder);
        makePerformOnActionClick(builder);
        makeOnClickEmitters(builder);
        makeOnActionClickEmitters(builder);
    }

    private void makeInjectViews(TypeSpec.Builder builder) {
        final CodeBlock.Builder codeBlock = CodeBlock.builder();
        for (final Map.Entry<Element, Integer> entry : mInjectView.entrySet()) {
            codeBlock.addStatement("target.$L = $T.findById(root, $L)", entry.getKey().getSimpleName(),
                    VIEWS, entry.getValue());
        }
        for (final int[] viewIds : mOnClick.values()) {
            for (final int viewId : viewIds) {
                codeBlock.addStatement("emitOnClick$L(root, target)", viewId);
            }
        }
        builder.addMethod(MethodSpec.methodBuilder("injectViews")
                .addParameter(VIEW, "root")
                .addParameter(mOriginType, "target")
                .addCode(codeBlock.build())
                .build());
        builder.addMethod(MethodSpec.methodBuilder("injectViews")
                .addParameter(WINDOW, "root")
                .addParameter(mOriginType, "target")
                .addCode(codeBlock.build())
                .build());
    }

    private void makeOnCreate(TypeSpec.Builder builder) {
        final CodeBlock.Builder codeBlock = CodeBlock.builder();
        codeBlock.addStatement("$L.clear()", M_ON_ACTION_CLICK);
        for (final int[] viewIds : mOnActionClick.values()) {
            for (final int viewId : viewIds) {
                codeBlock.addStatement("emitOnActionClick$L(target)", viewId);
            }
        }
        builder.addMethod(MethodSpec.methodBuilder("onCreate")
                .addParameter(mOriginType, "target")
                .addStatement("$L.clear()", M_ON_CLICK)
                .addCode(codeBlock.build())
                .build());
    }

    private void makeOnResume(TypeSpec.Builder builder) {
        final CodeBlock.Builder codeBlock = CodeBlock.builder();
        codeBlock.beginControlFlow("for(final Map.Entry<$T, $T> entry : $L.entrySet())",
                VIEW, ON_CLICK_LISTENER, M_ON_CLICK);
        codeBlock.addStatement("entry.getKey().setOnClickListener(entry.getValue())");
        codeBlock.endControlFlow();
        builder.addMethod(MethodSpec.methodBuilder("onResume")
                .addParameter(mOriginType, "target")
                .addCode(codeBlock.build())
                .build());
    }

    private void makeOnPause(TypeSpec.Builder builder) {
        final CodeBlock.Builder codeBlock = CodeBlock.builder();
        codeBlock.beginControlFlow("for(final $T view : $L.keySet())", VIEW, M_ON_CLICK);
        codeBlock.addStatement("view.setOnClickListener(null)");
        codeBlock.endControlFlow();
        builder.addMethod(MethodSpec.methodBuilder("onPause")
                .addParameter(mOriginType, "target")
                .addCode(codeBlock.build())
                .build());
    }

    private void makeOnDestroy(TypeSpec.Builder builder) {
        builder.addMethod(MethodSpec.methodBuilder("onDestroy")
                .addParameter(mOriginType, "target")
                .addStatement("$L.clear()", M_ON_CLICK)
                .addStatement("$L.clear()", M_ON_ACTION_CLICK)
                .build());
    }

    private void makePerformOnActionClick(TypeSpec.Builder builder) {
        builder.addMethod(MethodSpec.methodBuilder("performActionClick")
                .addParameter(MENU_ITEM, "menuItem")
                .returns(TypeName.BOOLEAN)
                .addStatement("final $T listener = $L.get(menuItem.getItemId())",
                        MENU_ITEM_LISTENER, M_ON_ACTION_CLICK)
                .addStatement("return listener != null && listener.onMenuItemClick(menuItem)")
                .build());
    }

    private void makeOnClickEmitters(TypeSpec.Builder builder) {
        for (final Map.Entry<ExecutableElement, int[]> entry : mOnClick.entrySet()) {
            final ExecutableElement originMethod = entry.getKey();
            final String originMethodName = originMethod.getSimpleName().toString();
            final List<? extends VariableElement> parameters = originMethod.getParameters();
            for (final int viewId : entry.getValue()) {
                final String methodName = "emitOnClick" + viewId;
                final CodeBlock.Builder codeBlock = CodeBlock.builder();
                codeBlock.addStatement("final $T view = $T.findById(root, $L)", VIEW, VIEWS, viewId);
                codeBlock.add("$L.put(view, new $T() {\n", M_ON_CLICK, ON_CLICK_LISTENER);
                codeBlock.indent();
                codeBlock.add("@Override\n");
                codeBlock.add("public void onClick($T v) {\n", VIEW);
                codeBlock.indent();
                if (parameters.isEmpty()) {
                    codeBlock.addStatement("target.$L()", originMethodName);
                } else if (parameters.size() == 1
                        && JavacUtils.isSubtype(parameters.get(0), "android.view.View")) {
                    codeBlock.addStatement("target.$L(v)", originMethodName);
                } else {
                    JavacLog.error(originMethod, "Invalid method parameters size. Expected () or (View)");
                }
                codeBlock.unindent();
                codeBlock.add("}\n");
                codeBlock.unindent();
                codeBlock.add("});\n");
                builder.addMethod(MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(VIEW, "root")
                        .addParameter(mOriginType, "target", Modifier.FINAL)
                        .addCode(codeBlock.build())
                        .build());
                builder.addMethod(MethodSpec.methodBuilder(methodName)
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(WINDOW, "root")
                        .addParameter(mOriginType, "target", Modifier.FINAL)
                        .addCode(codeBlock.build())
                        .build());
            }
        }
    }

    private void makeOnActionClickEmitters(TypeSpec.Builder builder) {
        for (final Map.Entry<ExecutableElement, int[]> entry : mOnActionClick.entrySet()) {
            for (final int viewId : entry.getValue()) {
                final ExecutableElement originMethod = entry.getKey();
                final CodeBlock.Builder codeBlock = CodeBlock.builder();
                codeBlock.add("$L.put($L, new $T() {\n", M_ON_ACTION_CLICK, viewId, MENU_ITEM_LISTENER);
                codeBlock.indent();
                codeBlock.add("@Override\n");
                codeBlock.add("public boolean onMenuItemClick($T item) {\n", MENU_ITEM);
                codeBlock.indent();
                final TypeKind returnKind = originMethod.getReturnType().getKind();
                final List<? extends VariableElement> params = originMethod.getParameters();
                final boolean isEmptyParams = params.isEmpty();
                final boolean isMenuItemParam = !isEmptyParams
                        && JavacUtils.isSubtype(params.get(0), "android.view.MenuItem");
                if (TypeKind.BOOLEAN == returnKind && isEmptyParams) {
                    codeBlock.addStatement("return target.$L()", originMethod.getSimpleName());
                } else if (TypeKind.BOOLEAN == returnKind && isMenuItemParam) {
                    codeBlock.addStatement("return target.$L(item)", originMethod.getSimpleName());
                } else if (TypeKind.VOID == returnKind && isEmptyParams) {
                    codeBlock.addStatement("target.$L()", originMethod.getSimpleName());
                    codeBlock.addStatement("return true");
                } else if (TypeKind.VOID == returnKind && isMenuItemParam) {
                    codeBlock.addStatement("target.$L(item)", originMethod.getSimpleName());
                    codeBlock.addStatement("return true");
                } else {
                    JavacLog.error(originMethod, "Invalid method parameters size or return type. " +
                            "Expected [void|boolean]() or [void|boolean](MenuItem)");
                    codeBlock.addStatement("return false");
                }
                codeBlock.unindent();
                codeBlock.add("}\n");
                codeBlock.unindent();
                codeBlock.add("});\n");
                builder.addMethod(MethodSpec.methodBuilder("emitOnActionClick" + viewId)
                        .addModifiers(Modifier.PRIVATE)
                        .addParameter(mOriginType, "target", Modifier.FINAL)
                        .addCode(codeBlock.build())
                        .build());
            }
        }
    }


}
