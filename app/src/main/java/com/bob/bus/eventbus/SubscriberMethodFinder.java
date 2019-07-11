package com.bob.bus.eventbus;

import org.greenrobot.eventbus.EventBusException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xhb on 2019/6/19.
 */

public class SubscriberMethodFinder {

    //不允许 订阅方法被 这些修饰符 修饰
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;
    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;

    //是否忽略索引策略 默认不忽略
    private final boolean ignoreGeneratedIndex;

    //缓存 Class<?> --> 注册的class类  映射多个 被@Subscribe修饰的方法（List<SubscriberMethod>）
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE = new ConcurrentHashMap<>();

    //默认创建了四个中间件 固定的
    private static final int POOL_SIZE = 4;
    private static final FindState[] FIND_STATE_POOL = new FindState[POOL_SIZE];

    private final boolean strictMethodVerification;

    private List<SubscriberInfoIndex> subscriberInfoIndexes;

    /**
     * @Desc 构造函数
     */
    public SubscriberMethodFinder(
            List<SubscriberInfoIndex> subscriberInfoIndexes,
            boolean strictMethodVerification,
            boolean ignoreGeneratedIndex)
    {
        this.subscriberInfoIndexes = subscriberInfoIndexes;
        this.strictMethodVerification = strictMethodVerification;
        this.ignoreGeneratedIndex = ignoreGeneratedIndex;
    }

    /**
     * @Desc 寻找Class类总所有被@Subscribe修饰的方法
     */
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        //缓存是否有该集合   List<SubscriberMethod>
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberClass != null) {
            return subscriberMethods;
        }
        //缓存没有 继续走
        if (ignoreGeneratedIndex) {
            //反射获取
            subscriberMethods = findUsingReflection(subscriberClass);
        } else {
            //默认获取index apt策略
            subscriberMethods = findUsingInfo(subscriberClass);
        }

        return null;
    }

    private List<SubscriberMethod> findUsingInfo(Class<?> subscriberClass) {
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            findState.subscriberInfo = getSubscriberInfo(findState);
            if (findState.subscriberInfo != null) {
                SubscriberMethod[] array = findState.subscriberInfo.getSubscriberMethods();
                for (SubscriberMethod subscriberMethod : array) {
                    if (findState.checkAdd(subscriberMethod.method, subscriberMethod.eventType)) {
                        findState.subscriberMethods.add(subscriberMethod);
                    }
                }
            } else {
                findUsingReflectionInSingleClass(findState);
            }
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    /**
     * @Desc 获取订阅信息
     */
    private SubscriberInfo getSubscriberInfo(FindState findState) {
        if (findState.subscriberInfo != null && findState.subscriberInfo.getSuperSubscriberInfo() != null) {
            SubscriberInfo superSubscriberInfo = findState.subscriberInfo.getSuperSubscriberInfo();
            if (findState.clazz == superSubscriberInfo.getSubscriberClass()) {
                return superSubscriberInfo;
            }
        }
        if (subscriberInfoIndexes != null) {
            for (SubscriberInfoIndex index : subscriberInfoIndexes) {
                SubscriberInfo info = index.getSubscriberInfo(findState.clazz);
                if (info != null) {
                    return info;
                }
            }
        }
        return null;
    }

    /**
     * 拿过了该类以及父类们的 所有注解的方法  并回收中间件findState
     */
    private List<SubscriberMethod> getMethodsAndRelease(FindState findState) {
        List<SubscriberMethod> subscriberMethods = new ArrayList<>(findState.subscriberMethods);
        findState.recycle();
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                if (FIND_STATE_POOL[i] == null) {
                    FIND_STATE_POOL[i] = findState;
                    break;
                }
            }
        }
        return subscriberMethods;
    }


    /**
     * @Desc 通过反射获取 subscriberClass 内多个被@Subscribe修饰的方法集合
     */
    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
        //获取一个中间件
        FindState findState = prepareFindState();
        //FindState与 注册EventBus的类 相关联
        findState.initForSubscriber(subscriberClass);
        //clazz 该类 该类的父类们 所以一直循环寻找
        while (findState.clazz != null) {
            findUsingReflectionInSingleClass(findState);
            findState.moveToSuperclass();
        }
        return getMethodsAndRelease(findState);
    }

    /**
     * @desc 反射解析被订阅的方法
     */
    private void findUsingReflectionInSingleClass(FindState findState) {
        Method[] methods;
        try {
            //暴力反射获取所有的方法
            //EventBus订阅方法必须是Public的 但是这里由获取私有的  就是在下面抛出异常 让开发者知道问题出现在哪里
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable throwable) {
            //那就只能拿到public修饰的所有方法
            methods = findState.clazz.getMethods();
        }
        //遍历方法
        for (Method method : methods) {
            //权限修饰符
            int modifiers = method.getModifiers();
            //方法修饰的必须是public 并且不是抽象的 静态的 BRIDGE或SYNTHETIC 两个修饰的
            if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                //方法参数类型
                Class<?>[] parameterTypes = method.getParameterTypes();
                //parameterTypes 为我们传递的Event的Class类型
                if (parameterTypes.length == 1) {
                    Subscribe subscribeAnnotation = method.getAnnotation(Subscribe.class);
                    //获取Subscribe注解
                    if (subscribeAnnotation != null) {
                        Class<?> eventType = parameterTypes[0];
                        if (findState.checkAdd(method, eventType)) {
                            ThreadMode threadMode = subscribeAnnotation.threadMode();
                            findState.subscriberMethods.add(new SubscriberMethod(method, threadMode,eventType, subscribeAnnotation.priority(), subscribeAnnotation.sticky()));
                        }
                    }
                } else if (strictMethodVerification && method.isAnnotationPresent(Subscribe.class)) {
                    //抛出异常
                }
            } else {
                String methodName = method.getDeclaringClass().getName() + "." + method.getName();
                throw new EventBusException(methodName + " is a illegal @Subscribe method: must be public, non-static, and non-abstract");
            }

        }
    }


    /**
     * @return 拿一个初始状态的中间件
     */
    private FindState prepareFindState() {
        synchronized (FIND_STATE_POOL) {
            for (int i = 0; i < POOL_SIZE; i++) {
                FindState state = FIND_STATE_POOL[i];
                if (state != null) {
                    //拿走数组的对象 如果还有被没有拿走的对象则取走 并且将数组保存对象的索引置空，解除引用
                    FIND_STATE_POOL[i] = null;
                    return state;
                }
            }
        }
        //如果数组的缓存对象都被拿走了 则手动创建一个
        return new FindState();
    }


    /**
     * @desc 内部类 理解为中间件
     */
    static class FindState {
        //注册EventBus的类
        Class<?> subscriberClass;
        //这里的clazz是正在解析的注解类以及父类， 第一个是subscriberClass 下面就是 他的父类 。。。
        Class<?> clazz;

        //是否需要解析父类 默认不跳过， 如果获取方法能调用getDeclaredMethods() 这个时候就跳过
        boolean skipSuperClasses;
        SubscriberInfo subscriberInfo;

        //Class 为注册的类， Object 为订阅方法
        final Map<Class, Object> anyMethodByEventType = new HashMap<>();

        final List<SubscriberMethod> subscriberMethods = new ArrayList<>();
        final Map<String, Class> subscriberClassByMethodKey = new HashMap<>();
        final StringBuilder methodKeyBuilder = new StringBuilder(128);

        public void initForSubscriber(Class<?> subscriberClass) {
            this.subscriberClass = clazz = subscriberClass;
            skipSuperClasses = false;
            subscriberInfo = null;
        }

        boolean checkAdd(Method method, Class<?> eventType) {
            Object existing = anyMethodByEventType.put(eventType, method);
            if (existing == null) {
                return true;
            } else {
                if (existing instanceof Method) {
                    if (!checkAddWithMethodSignature((Method) existing, eventType)) {
                        throw new IllegalStateException();
                    }
                    anyMethodByEventType.put(eventType, this);
                }
                return checkAddWithMethodSignature(method, eventType);
            }
        }

        /**
         * TODO
         */
        private boolean checkAddWithMethodSignature(Method method, Class<?> eventType) {
            methodKeyBuilder.setLength(0);
            methodKeyBuilder.append(method.getName());
            methodKeyBuilder.append('>').append(eventType.getName());

            String methodKey = methodKeyBuilder.toString();
            Class<?> methodClass = method.getDeclaringClass();
            Class<?> methodClassOld = subscriberClassByMethodKey.put(methodKey, methodClass);
            if (methodClassOld == null || methodClassOld.isAssignableFrom(methodClass)) {
                return true;
            } else {
                subscriberClassByMethodKey.put(methodKey, methodClassOld);
                return false;
            }
        }

        //往上解析父类
        public void moveToSuperclass() {
            if (skipSuperClasses) {
                clazz = null;
            } else {
                clazz = clazz.getSuperclass();
                String clazzName = clazz.getName();
                if (clazzName.startsWith("java.") || clazzName.startsWith("javax.") || clazzName.startsWith("android.")) {
                    clazz = null;
                }
            }
        }

        public void recycle() {
            subscriberMethods.clear();
            anyMethodByEventType.clear();
            subscriberClassByMethodKey.clear();
            methodKeyBuilder.setLength(0);
            subscriberClass = null;
            clazz = null;
            skipSuperClasses = false;
            subscriberInfo = null;
        }
    }

}
