package droidkit.processor;

import com.squareup.javapoet.*;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * @author Daniel Serdyukov
 */
class FragmentMaker implements ClassMaker {

    private static final String ANDROID_APP_DIALOG_FRAGMENT = "android.app.DialogFragment";

    private static final String ANDROID_SUPPORT_V4_APP_DIALOG_FRAGMENT = "android.support.v4.app.DialogFragment";

    private final JavacProcessingEnvironment mEnv;

    private TypeElement mOriginElement;

    private ClassName mOriginType;

    private TypeName mSuperType;

    private ClassName mLifecycle;

    private boolean mIdDialog;

    FragmentMaker(ProcessingEnvironment env) {
        mEnv = (JavacProcessingEnvironment) env;
    }

    FragmentMaker withOriginType(TypeElement originType) {
        mOriginElement = originType;
        mOriginType = ClassName.get(originType);
        mSuperType = TypeName.get(originType.getSuperclass());
        mIdDialog = JavacUtils.isSubtype(originType, ANDROID_APP_DIALOG_FRAGMENT) ||
                JavacUtils.isSubtype(originType, ANDROID_SUPPORT_V4_APP_DIALOG_FRAGMENT);
        return this;
    }

    FragmentMaker withLifecycle(ClassName lifecycle) {
        mLifecycle = lifecycle;
        return this;
    }

    @Override
    public JavaFile make() throws IOException {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(mOriginType.simpleName() + PROXY)
                .addOriginatingElement(mOriginElement)
                .superclass(mSuperType);
        makeFields(builder);
        makeMethods(builder);
        final TypeSpec spec = builder.build();
        final JavaFile javaFile = JavaFile.builder(mOriginType.packageName(), spec)
                .addFileComment(AUTO_GENERATED)
                .build();
        final JavaFileObject sourceFile = mEnv.getFiler().createSourceFile(javaFile.packageName + "."
                + spec.name, mOriginElement);
        try (final Writer writer = new BufferedWriter(sourceFile.openWriter())) {
            javaFile.writeTo(writer);
        }
        JavacUtils.extend(mOriginElement, spec.name);
        return javaFile;
    }

    private void makeFields(TypeSpec.Builder builder) {
        builder.addField(FieldSpec.builder(mLifecycle, M_LIFECYCLE, Modifier.PRIVATE, Modifier.FINAL)
                .initializer("new $T()", mLifecycle)
                .build());
    }

    private void makeMethods(TypeSpec.Builder builder) {
        if (!mIdDialog) {
            makeOnViewCreated(builder);
        }
    }

    private void makeOnViewCreated(TypeSpec.Builder builder) {
        builder.addMethod(MethodSpec.methodBuilder("onViewCreated")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("android.view", "View"), "view")
                .addParameter(ClassName.get("android.os", "Bundle"), "savedInstanceState")
                .addStatement("super.onViewCreated(view, savedInstanceState)")
                .addStatement("$L.injectViews(view, ($T) this)", M_LIFECYCLE, mOriginType)
                .build());
    }

}