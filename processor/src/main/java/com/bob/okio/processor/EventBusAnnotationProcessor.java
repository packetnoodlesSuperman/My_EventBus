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
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
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

/**
 * Created by xhb on 2019/6/30.
 */
//指明对@Subscribe进行筛选，会执行到process中，在这里进行操作生成什么样的文件。
@SupportedAnnotationTypes({"org.greenrobot.eventbus.Subscribe"})
//指明option的name，可以根据processingEnv.getOptions().get(key)去获取相应的值
@SupportedOptions({"eventBusIndex", "verbose"})
public class EventBusAnnotationProcessor extends AbstractProcessor {

    //是否开启 有关轮询次数 的日志
    private boolean verbose;
    //轮询次数
    private int round;
    //是否已经执行完
    private boolean writerRoundDone;
    //根据class作key、method作value缓存到集合中
    private final ListMap<TypeElement, ExecutableElement> methodsByClass = new ListMap();

    private final Set<TypeElement> classesToSkip = new HashSet();

    /**
     * @Desc 这相当于每个处理器的主函数main() 你在这里写你的扫描、评估和处理注解的代码，以及生成Java文件
     * @param annotations
     * @param env 可以让你查询出包含特定注解的被注解元素
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        //获取日志 用于保存错误日志
        Messager messager = this.processingEnv.getMessager();

        try {
            // Map<String, String> getOptions() 返回指定的参数选项 拿到的是传递给注释处理工具的Map选项
            // 怎样获取呢 下面就是例子
            // defaultConfig {
            //     javaCompileOptions {
            //         annotationProcessorOptions {
            //             arguments = [ eventBusIndex : 'org.greenrobot.eventbusperf.MyEventBusIndex' ]
            //         }
            //     }
            // }
            //
            // public synchronized void init(ProcessingEnvironment processingEnv) {
            //     Map<String, String> options = processingEnv.getOptions();
            //     if (MapUtils.isNotEmpty(options)) {
            //         moduleName = options.get('eventBusIndex');
            //     }
            // }
            String index = (String) this.processingEnv.getOptions().get("eventBusIndex");

            //没有指定路径 就打印错误日志 并结束执行流程
            //index --> org.greenrobot.eventbusperf.MyEventBusIndex 就是全路径类型
            if (index == null) {
                messager.printMessage(Kind.ERROR, "No option eventBusIndex ");
                return false;
            }
            //是否打印 轮次相关 日志
            this.verbose = Boolean.parseBoolean((String) this.processingEnv.getOptions().get("verbose"));
            /**
             * indexOf 和 lastIndexOf 都是索引文件
             * lastIndexOf 返回指定字符在此字符串中最后一次出现处的索引，如果此字符串中没有这样的字符，则返回 -1。
             */
            //46在ASCII 代表"." (点) 返回最后一个点的位置
            int lastPeriod = index.lastIndexOf(46);
            //截取包名
            String indexPackage = lastPeriod != -1 ? index.substring(0, lastPeriod) : null;
            //标记轮次增加
            ++this.round;
            //verbose仅用于判断是否打印这段日志
            if (this.verbose) {
                messager.printMessage(Kind.NOTE, "Processing round " + this.round + ", new annotations: " + !annotations.isEmpty() + ", processingOver: " + env.processingOver());
            }

            // 判断注解处理最后一轮是否结束 并且 是否有该处理器支持的注解或代码中是否有使用注解
            if (env.processingOver() && !annotations.isEmpty()) {
                //若注解处理过程已经结束，但是仍然有注解集合传入，则打印错误日志
                messager.printMessage(Kind.ERROR, "Unexpected processing state: annotations still available after processing over");
                return false;
            }
            //再对注解集合做一次非空检查
            if (annotations.isEmpty()) {
                return false;
            }
            //若已经生成完成，也打印错误日志
            if (this.writerRoundDone) {
                messager.printMessage(Kind.ERROR, "Unexpected processing state: annotations still available after writing.");
            }

            //以上是检查完毕，下面开始进行解析记录
            //记录合法的订阅方法，保存进methodsByClass集合
            this.collectSubscribers(annotations, env, messager);
            //记录不处理的订阅方法
            this.checkForSubscribersToSkip(messager, indexPackage);

            if (!this.methodsByClass.isEmpty()) {
                //开始手动写类  index代表全路径类
                this.createInfoIndexFile(index);
            } else {
                messager.printMessage(Kind.WARNING, "No @Subscribe annotations found");
            }

            //标记已经轮询完成
            this.writerRoundDone = true;
        } catch (RuntimeException e) {
            e.printStackTrace();
            messager.printMessage(Kind.ERROR, "Unexpected error in EventBusAnnotationProcessor: " + e);
        }
        // 最终返回true，表示已经正确处理
        return true;
    }

    /**
     * 记录合法的订阅方法，保存进methodsByClass集合
     */
    private void collectSubscribers(Set<? extends TypeElement> annotations, RoundEnvironment env, Messager messager) {
        //遍历该处理器注册的注解元素的集合
        Iterator<? extends TypeElement> iterator = annotations.iterator();

        while (iterator.hasNext()) {
            //@Subscriber注解  因为注解类型也是 TypeElement  （接口和类 也是TypeElement）
            TypeElement annotation = (TypeElement) iterator.next();
            //获取所有使用了该注解的元素集合，在这里即所有添加了@Subscribe的方法的元素
            Set<? extends Element> elements = env.getElementsAnnotatedWith(annotation);
            Iterator<? extends Element> elementIterator = elements.iterator();

            while(elementIterator.hasNext()) {
                //获取被@Subscriber注解的元素
                Element element = (Element)elementIterator.next();

                //ExecutableElement 代表的是方法体 以为@Subscribe修饰的是方法
                if (element instanceof ExecutableElement) {
                    //ExecutableElement表示可执行方法的元素，因为@Subscribe只能用在method上
                    ExecutableElement method = (ExecutableElement)element;
                    //合法性校验
                    if (this.checkHasNoErrors(method, messager)) {
                        //获取ExecutableElement所在类的元素，即订阅方法所在的订阅者类
                        TypeElement classElement = (TypeElement)method.getEnclosingElement();
                        //根据class作key、method作value缓存到集合中
                        this.methodsByClass.putElement(classElement, method);
                    }
                } else {
                    messager.printMessage(Kind.ERROR, "@Subscribe is only valid for methods", element);
                }
            }
        }
    }

    /**
     * 合法性校验
     */
    private boolean checkHasNoErrors(ExecutableElement element, Messager messager) {
        // 判断方法修饰符，不能是静态方法
        if (element.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(Kind.ERROR, "Subscriber method must not be static", element);
            return false;
        }
        // 方法必须是public的
        if (!element.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Kind.ERROR, "Subscriber method must be public", element);
            return false;
        }

        //获取该方法的参数集合
        List<? extends VariableElement> parameters = element.getParameters();
        //参数个数必须是一个
        if (parameters.size() != 1) {
            messager.printMessage(Kind.ERROR, "Subscriber method must hava exactly 1 parameter", element);
            return false;
        } else {
            return true;
        }
    }


    /**
     * 筛选订阅方法
     * @param myPackage 包名
     */
    private void checkForSubscribersToSkip(Messager messager, String myPackage) {
        //methodsByClass --> 根据class作key、method作value缓存到集合中
        Iterator var3 = this.methodsByClass.keySet().iterator();
        // 遍历集合key，依次检查订阅者类
        while(true) {
            while(var3.hasNext()) {
                //skipCandidate 为 class
                TypeElement skipCandidate = (TypeElement)var3.next();
                //遍历该类 或者父类
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

                    //类是可见的 （public修饰的等）
                    //获取该类所有被@Subscribe修饰的方法
                    List<ExecutableElement> methods = (List)this.methodsByClass.get(subscriberClass);
                    if (methods != null) {
                        Iterator var7 = methods.iterator();

                        while(var7.hasNext()) {
                            //拿到一个被@Subscribe修饰的方法
                            ExecutableElement method = (ExecutableElement)var7.next();
                            String skipReason = null;
                            //获取方法中的第一个删除  VariableElement -->  Field
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

    /**
     * @Desc 判断该类是是否可见 就是判断修饰符
     */
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
                //是否是同一级的包
                visible = myPackage.equals(subscriberPackage);
            }
        } else {
            visible = false;
        }
        return visible;
    }

    private PackageElement getPackageElement(TypeElement subscriberClass) {
        Element candidate;
        for(candidate = subscriberClass.getEnclosingElement(); !(candidate instanceof PackageElement); candidate = candidate.getEnclosingElement()) {
            ;
        }

        return (PackageElement)candidate;
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

    /**
     *  即element是代表程序的一个元素，这个元素可以是：包、类/接口、属性变量、方法/方法形参、泛型参数。
     *  element是java-apt(编译时注解处理器)技术的基础，因此如果要编写此类框架，熟悉element是必须的。
     *
     *  com.bob.eventbus (package 包)                        --> PackageElement
     *      --- EventBus<T>.java (Class 类)                  --> TypeElement 类的泛型T属于 TypeParameterElement
     *            --- int a = 10 (Field 属性变量)            --> VariableElement
     *            --- List<String> list (Field 属性变量)     --> VariableElement
     *            --- Enum<?> enum (Field 属性变量)          --> VariableElement
     *            --- public void post(Object event) {}      --> 方法体 Void post 属于 ExecutableElement  参数object 属于 VariableElement
     *            --- 构造器 初始代码块 等也是ExecutableElement
     *      --- ICallback.java(interface 接口)               --> TypeElement
     *      --- @Subscriber (Annotaion 注解)                 --> TypeElement
     *
     * 当一个Element 是TypeElement 时我们主要关心他的全限定名继承的类或实现的接口等信息，这些我们都可以通过对于的方法获取到
     * 当一个Element是VariableElement是，我们也可以通过相应的方法获取相应的信息
     * 当一个Element对应的是一个ExecutableElement (方法)时，我们关心的是方法的参数和参数的类型，我们可以通过以下的方法获取
     *
     * ElementKind
     *     PACKAGE,（）   ENUM,     CLASS,（一个类） ANNOTATION_TYPE,  INTERFACE,
     *        ENUM_CONSTANT,     FIELD,（一个字段）   PARAMETER,      LOCAL_VARIABLE,      EXCEPTION_PARAMETER,
     *     METHOD,（一个方法）  CONSTRUCTOR,   STATIC_INIT,     INSTANCE_INIT,   TYPE_PARAMETER,
     *       OTHER,    RESOURCE_VARIABLE;
     *
     * TypeKind <该类是一个媒介,定义了java中各种类型所对应的枚举>
     *    BOOLEAN《boolean的原始类型》, BYTE《byte的原始类型》, SHORT《short的原始类型》, INT《int的原始类型》, LONG《long的原始类型》,
     *    CHAR《char的原始类型》, FLOAT《float的原始类型》, DOUBLE《 double的原始类型》, VOID《代表void的伪类型》, NONE《NoType 代表没有合适的类型与之对应的伪类型》,
     *    NULL《代表null的类型》, ARRAY《代表array数组的类型》, DECLARED《 代表类或者接口的类型》, ERROR《 代表无法进行解析的类或接口类型》,
     *    TYPEVAR《类型变量 如一个类声明如下: C<T,S> ,那么T,S就是类型变量》,
     *    WILDCARD《通配符.如:?,? extends Number,? super T》,
     *    PACKAGE《代表包的伪类型》, EXECUTABLE《代表方法,构造器,初始代码块》,
     *    OTHER《 保留类型》, UNION《联合类型  jdk1.7中的 try multi-catch 中异常的参数就是联合类型》,
     *    INTERSECTION《交集类型 如一个类有泛型参数,如<T extends Number & Runnable>,那么T extends Number & Runnable 就是交集类型》
     *
     *  TypeMirror 代表了java编程语言中的类型,包括原生类型,声明类型(类和接口)，数组类型，类型变量,null类型.
     *             同时也包括通配符,可执行语句的签名和返回类型,包和void所对应的伪类型
     *  https://blog.csdn.net/qq_26000415/article/details/82260960
     */
    private TypeElement getSuperclass(TypeElement type) {
        /**
         * TypeKind.DECLARED  类或者接口的类型
         * 而类或者接口类型为 TypeElement
         */
        if (type.getSuperclass().getKind() == TypeKind.DECLARED) {
            TypeElement superclass = (TypeElement)this.processingEnv.getTypeUtils().asElement(type.getSuperclass());
            //获取该类的全类名
            String name = superclass.getQualifiedName().toString();
            //不能是系统类
            return !name.startsWith("java.") && !name.startsWith("javax.") && !name.startsWith("android.") ? superclass : null;
        } else {
            return null;
        }
    }

    /**
     * @Desc 精华所在 手动写一个class
     *
     * 使用者在程序运行期间，可以动态的写Java Class，不需要生成任何.Class文件就可以完全在内存中编译，加载，实例化
     * 1、需要用到的组件介绍
     *      1）JavaCompiler：用于编译Java Code。
     *      2）CharSequenceJavaFileObject：用于保存Java Code，提供方法给JavaCompiler获取String形式的Java Code。
     *      3）ClassFileManager：用于JavaCompiler将编译好后的Class文件保存在指定对象中。
     *      4）JavaClassObject：ClassFileManager告诉JavaCompiler需要将Class文件保存在JavaClassObject中，
     *                          但是由JavaClassObject来决定最终以byte流来保存数据。
     *      5）DynamicClassLoader：自定义类加载器，用于加载最后的二进制Class
     */
    private void createInfoIndexFile(String index) {
        BufferedWriter writer = null;

        try {
            JavaFileObject sourceFile = this.processingEnv.getFiler().createSourceFile(index, new Element[0]);


            writer = new BufferedWriter(sourceFile.openWriter());

        } catch (Exception e) {

        }
    }
}