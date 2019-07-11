package com.bob.okio.processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.tools.JavaFileObject;
import javax.tools.Diagnostic.Kind;

//@SupportedAnnotationTypes({"org.greenrobot.eventbus.Subscribe"})
//@SupportedOptions({"eventBusIndex", "verbose"})
public class DemoEventBusAnnotationProcessor
        //extends AbstractProcessor  防止运行 先注释了
{
    protected ProcessingEnvironment processingEnv;

    public static final String OPTION_EVENT_BUS_INDEX = "eventBusIndex";
    public static final String OPTION_VERBOSE = "verbose";
    private final ListMap<TypeElement, ExecutableElement> methodsByClass = new ListMap();
    private final Set<TypeElement> classesToSkip = new HashSet();
    private boolean writerRoundDone;
    private int round;
    private boolean verbose;

    public DemoEventBusAnnotationProcessor() {
    }

    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        Messager messager = this.processingEnv.getMessager();

        try {
            String index = (String)this.processingEnv.getOptions().get("eventBusIndex");
            if (index == null) {
                messager.printMessage(Kind.ERROR, "No option eventBusIndex passed to annotation processor");
                return false;
            }

            this.verbose = Boolean.parseBoolean((String)this.processingEnv.getOptions().get("verbose"));
            int lastPeriod = index.lastIndexOf(46);
            String indexPackage = lastPeriod != -1 ? index.substring(0, lastPeriod) : null;
            ++this.round;
            if (this.verbose) {
                messager.printMessage(Kind.NOTE, "Processing round " + this.round + ", new annotations: " + !annotations.isEmpty() + ", processingOver: " + env.processingOver());
            }

            if (env.processingOver() && !annotations.isEmpty()) {
                messager.printMessage(Kind.ERROR, "Unexpected processing state: annotations still available after processing over");
                return false;
            }

            if (annotations.isEmpty()) {
                return false;
            }

            if (this.writerRoundDone) {
                messager.printMessage(Kind.ERROR, "Unexpected processing state: annotations still available after writing.");
            }

            this.collectSubscribers(annotations, env, messager);
            this.checkForSubscribersToSkip(messager, indexPackage);
            if (!this.methodsByClass.isEmpty()) {
                this.createInfoIndexFile(index);
            } else {
                messager.printMessage(Kind.WARNING, "No @Subscribe annotations found");
            }

            this.writerRoundDone = true;
        } catch (RuntimeException var7) {
            var7.printStackTrace();
            messager.printMessage(Kind.ERROR, "Unexpected error in EventBusAnnotationProcessor: " + var7);
        }

        return true;
    }

    private void collectSubscribers(Set<? extends TypeElement> annotations, RoundEnvironment env, Messager messager) {
        Iterator var4 = annotations.iterator();

        while(var4.hasNext()) {
            TypeElement annotation = (TypeElement)var4.next();
            Set<? extends Element> elements = env.getElementsAnnotatedWith(annotation);
            Iterator var7 = elements.iterator();

            while(var7.hasNext()) {
                Element element = (Element)var7.next();
                if (element instanceof ExecutableElement) {
                    ExecutableElement method = (ExecutableElement)element;
                    if (this.checkHasNoErrors(method, messager)) {
                        TypeElement classElement = (TypeElement)method.getEnclosingElement();
                        this.methodsByClass.putElement(classElement, method);
                    }
                } else {
                    messager.printMessage(Kind.ERROR, "@Subscribe is only valid for methods", element);
                }
            }
        }

    }

    private boolean checkHasNoErrors(ExecutableElement element, Messager messager) {
        if (element.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(Kind.ERROR, "Subscriber method must not be static", element);
            return false;
        } else if (!element.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Kind.ERROR, "Subscriber method must be public", element);
            return false;
        } else {
            List<? extends VariableElement> parameters = element.getParameters();
            if (parameters.size() != 1) {
                messager.printMessage(Kind.ERROR, "Subscriber method must have exactly 1 parameter", element);
                return false;
            } else {
                return true;
            }
        }
    }

    private void checkForSubscribersToSkip(Messager messager, String myPackage) {
        Iterator var3 = this.methodsByClass.keySet().iterator();

        while(true) {
            while(var3.hasNext()) {
                TypeElement skipCandidate = (TypeElement)var3.next();

                for(TypeElement subscriberClass = skipCandidate; subscriberClass != null; subscriberClass = this.getSuperclass(subscriberClass)) {
                    if (!this.isVisible(myPackage, subscriberClass)) {
                        boolean added = this.classesToSkip.add(skipCandidate);
                        if (added) {
                            String msg;
                            if (subscriberClass.equals(skipCandidate)) {
                                msg = "Falling back to reflection because class is not public";
                            } else {
                                msg = "Falling back to reflection because " + skipCandidate + " has a non-public super class";
                            }

                            messager.printMessage(Kind.NOTE, msg, subscriberClass);
                        }
                        break;
                    }

                    List<ExecutableElement> methods = (List)this.methodsByClass.get(subscriberClass);
                    if (methods != null) {
                        Iterator var7 = methods.iterator();

                        while(var7.hasNext()) {
                            ExecutableElement method = (ExecutableElement)var7.next();
                            String skipReason = null;
                            VariableElement param = (VariableElement)method.getParameters().get(0);
                            TypeMirror typeMirror = this.getParamTypeMirror(param, messager);
                            if (!(typeMirror instanceof DeclaredType) || !(((DeclaredType)typeMirror).asElement() instanceof TypeElement)) {
                                skipReason = "event type cannot be processed";
                            }

                            if (skipReason == null) {
                                TypeElement eventTypeElement = (TypeElement)((DeclaredType)typeMirror).asElement();
                                if (!this.isVisible(myPackage, eventTypeElement)) {
                                    skipReason = "event type is not public";
                                }
                            }

                            if (skipReason != null) {
                                boolean added = this.classesToSkip.add(skipCandidate);
                                if (added) {
                                    String msg = "Falling back to reflection because " + skipReason;
                                    if (!subscriberClass.equals(skipCandidate)) {
                                        msg = msg + " (found in super class for " + skipCandidate + ")";
                                    }

                                    messager.printMessage(Kind.NOTE, msg, param);
                                }
                                break;
                            }
                        }
                    }
                }
            }

            return;
        }
    }

    private TypeMirror getParamTypeMirror(VariableElement param, Messager messager) {
        TypeMirror typeMirror = param.asType();
        if (typeMirror instanceof TypeVariable) {
            TypeMirror upperBound = ((TypeVariable)typeMirror).getUpperBound();
            if (upperBound instanceof DeclaredType) {
                if (messager != null) {
                    messager.printMessage(Kind.NOTE, "Using upper bound type " + upperBound + " for generic parameter", param);
                }

                typeMirror = upperBound;
            }
        }

        return typeMirror;
    }

    private TypeElement getSuperclass(TypeElement type) {
        if (type.getSuperclass().getKind() == TypeKind.DECLARED) {
            TypeElement superclass = (TypeElement)this.processingEnv.getTypeUtils().asElement(type.getSuperclass());
            String name = superclass.getQualifiedName().toString();
            return !name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("android.") ? superclass : null;
        } else {
            return null;
        }
    }

    private String getClassString(TypeElement typeElement, String myPackage) {
        PackageElement packageElement = this.getPackageElement(typeElement);
        String packageString = packageElement.getQualifiedName().toString();
        String className = typeElement.getQualifiedName().toString();
        if (packageString != null && !packageString.isEmpty()) {
            if (packageString.equals(myPackage)) {
                className = this.cutPackage(myPackage, className);
            } else if (packageString.equals("java.lang")) {
                className = typeElement.getSimpleName().toString();
            }
        }

        return className;
    }

    private String cutPackage(String paket, String className) {
        if (className.startsWith(paket + '.')) {
            return className.substring(paket.length() + 1);
        } else {
            throw new IllegalStateException("Mismatching " + paket + " vs. " + className);
        }
    }

    private PackageElement getPackageElement(TypeElement subscriberClass) {
        Element candidate;
        for(candidate = subscriberClass.getEnclosingElement(); !(candidate instanceof PackageElement); candidate = candidate.getEnclosingElement()) {
            ;
        }

        return (PackageElement)candidate;
    }

    private void writeCreateSubscriberMethods(BufferedWriter writer, List<ExecutableElement> methods, String callPrefix, String myPackage) throws IOException {
        Iterator var5 = methods.iterator();

        while(var5.hasNext()) {
            ExecutableElement method = (ExecutableElement)var5.next();
            List<? extends VariableElement> parameters = method.getParameters();
            TypeMirror paramType = this.getParamTypeMirror((VariableElement)parameters.get(0), (Messager)null);
            TypeElement paramElement = (TypeElement)this.processingEnv.getTypeUtils().asElement(paramType);
            String methodName = method.getSimpleName().toString();
            String eventClass = this.getClassString(paramElement, myPackage) + ".class";
            Subscribe subscribe = (Subscribe)method.getAnnotation(Subscribe.class);
            List<String> parts = new ArrayList();
            parts.add(callPrefix + "(\"" + methodName + "\",");
            String lineEnd = "),";
            if (subscribe.priority() == 0 && !subscribe.sticky()) {
                if (subscribe.threadMode() == ThreadMode.POSTING) {
                    parts.add(eventClass + lineEnd);
                } else {
                    parts.add(eventClass + ",");
                    parts.add("ThreadMode." + subscribe.threadMode().name() + lineEnd);
                }
            } else {
                parts.add(eventClass + ",");
                parts.add("ThreadMode." + subscribe.threadMode().name() + ",");
                parts.add(subscribe.priority() + ",");
                parts.add(subscribe.sticky() + lineEnd);
            }

            this.writeLine(writer, 3, (String[])parts.toArray(new String[parts.size()]));
            if (this.verbose) {
                this.processingEnv.getMessager().printMessage(Kind.NOTE, "Indexed @Subscribe at " + method.getEnclosingElement().getSimpleName() + "." + methodName + "(" + paramElement.getSimpleName() + ")");
            }
        }

    }

    private void createInfoIndexFile(String index) {
        BufferedWriter writer = null;

        try {
            JavaFileObject sourceFile = this.processingEnv.getFiler().createSourceFile(index);
            int period = index.lastIndexOf(46);
            String myPackage = period > 0 ? index.substring(0, period) : null;
            String clazz = index.substring(period + 1);
            writer = new BufferedWriter(sourceFile.openWriter());
            if (myPackage != null) {
                writer.write("package " + myPackage + ";\n\n");
            }

            writer.write("import org.greenrobot.eventbus.meta.SimpleSubscriberInfo;\n");
            writer.write("import org.greenrobot.eventbus.meta.SubscriberMethodInfo;\n");
            writer.write("import org.greenrobot.eventbus.meta.SubscriberInfo;\n");
            writer.write("import org.greenrobot.eventbus.meta.SubscriberInfoIndex;\n\n");
            writer.write("import org.greenrobot.eventbus.ThreadMode;\n\n");
            writer.write("import java.util.HashMap;\n");
            writer.write("import java.util.Map;\n\n");
            writer.write("/** This class is generated by EventBus, do not edit. */\n");
            writer.write("public class " + clazz + " implements SubscriberInfoIndex {\n");
            writer.write("    private static final Map<Class<?>, SubscriberInfo> SUBSCRIBER_INDEX;\n\n");
            writer.write("    static {\n");
            writer.write("        SUBSCRIBER_INDEX = new HashMap<Class<?>, SubscriberInfo>();\n\n");
            this.writeIndexLines(writer, myPackage);
            writer.write("    }\n\n");
            writer.write("    private static void putIndex(SubscriberInfo info) {\n");
            writer.write("        SUBSCRIBER_INDEX.put(info.getSubscriberClass(), info);\n");
            writer.write("    }\n\n");
            writer.write("    @Override\n");
            writer.write("    public SubscriberInfo getSubscriberInfo(Class<?> subscriberClass) {\n");
            writer.write("        SubscriberInfo info = SUBSCRIBER_INDEX.get(subscriberClass);\n");
            writer.write("        if (info != null) {\n");
            writer.write("            return info;\n");
            writer.write("        } else {\n");
            writer.write("            return null;\n");
            writer.write("        }\n");
            writer.write("    }\n");
            writer.write("}\n");
        } catch (IOException var14) {
            throw new RuntimeException("Could not write source for " + index, var14);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException var13) {
                    ;
                }
            }

        }

    }

    private void writeIndexLines(BufferedWriter writer, String myPackage) throws IOException {
        Iterator var3 = this.methodsByClass.keySet().iterator();

        while(var3.hasNext()) {
            TypeElement subscriberTypeElement = (TypeElement)var3.next();
            if (!this.classesToSkip.contains(subscriberTypeElement)) {
                String subscriberClass = this.getClassString(subscriberTypeElement, myPackage);
                if (this.isVisible(myPackage, subscriberTypeElement)) {
                    this.writeLine(writer, 2, "putIndex(new SimpleSubscriberInfo(" + subscriberClass + ".class,", "true,", "new SubscriberMethodInfo[] {");
                    List<ExecutableElement> methods = (List)this.methodsByClass.get(subscriberTypeElement);
                    this.writeCreateSubscriberMethods(writer, methods, "new SubscriberMethodInfo", myPackage);
                    writer.write("        }));\n\n");
                } else {
                    writer.write("        // Subscriber not visible to index: " + subscriberClass + "\n");
                }
            }
        }

    }

    private boolean isVisible(String myPackage, TypeElement typeElement) {
        Set<Modifier> modifiers = typeElement.getModifiers();
        boolean visible;
        if (modifiers.contains(Modifier.PUBLIC)) {
            visible = true;
        } else if (!modifiers.contains(Modifier.PRIVATE) && !modifiers.contains(Modifier.PROTECTED)) {
            String subscriberPackage = this.getPackageElement(typeElement).getQualifiedName().toString();
            if (myPackage == null) {
                visible = subscriberPackage.length() == 0;
            } else {
                visible = myPackage.equals(subscriberPackage);
            }
        } else {
            visible = false;
        }

        return visible;
    }

    private void writeLine(BufferedWriter writer, int indentLevel, String... parts) throws IOException {
        this.writeLine(writer, indentLevel, 2, parts);
    }

    private void writeLine(BufferedWriter writer, int indentLevel, int indentLevelIncrease, String... parts) throws IOException {
        this.writeIndent(writer, indentLevel);
        int len = indentLevel * 4;

        for(int i = 0; i < parts.length; ++i) {
            String part = parts[i];
            if (i != 0) {
                if (len + part.length() > 118) {
                    writer.write("\n");
                    if (indentLevel < 12) {
                        indentLevel += indentLevelIncrease;
                    }

                    this.writeIndent(writer, indentLevel);
                    len = indentLevel * 4;
                } else {
                    writer.write(" ");
                }
            }

            writer.write(part);
            len += part.length();
        }

        writer.write("\n");
    }

    private void writeIndent(BufferedWriter writer, int indentLevel) throws IOException {
        for(int i = 0; i < indentLevel; ++i) {
            writer.write("    ");
        }

    }
}

