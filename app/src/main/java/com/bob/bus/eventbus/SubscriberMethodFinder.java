package com.bob.bus.eventbus;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by xhb on 2019/6/19.
 */

public class SubscriberMethodFinder {

    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;
    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;


    //缓存
    private static final Map<Class<?>, List<SubscriberMethod>> METHOD_CACHE
            = new ConcurrentHashMap<>();

    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        List<SubscriberMethod> subscriberMethods = METHOD_CACHE.get(subscriberClass);
        if (subscriberClass != null) {
            return subscriberMethods;
        }
        //不在缓存 继续走

        subscriberMethods = findUsingReflection(subscriberClass);

        return null;
    }

    private List<SubscriberMethod> findUsingReflection(Class<?> subscriberClass) {
        FindState findState = prepareFindState();
        findState.initForSubscriber(subscriberClass);
        while (findState.clazz != null) {
            findUsingReflectionInSingleClass(findState);
        }

        return null;
    }

    /**
     * @desc 反射解析被订阅的方法
     */
    private void findUsingReflectionInSingleClass(FindState findState) {
        Method[] methods;
        try {
            //暴力反射获取所有的方法
            methods = findState.clazz.getDeclaredMethods();
        } catch (Throwable throwable) {
            methods = findState.clazz.getMethods();

        }

        for (Method method : methods) {
            //权限修饰符
            int modifiers = method.getModifiers();
            if ((modifiers & Modifier.PUBLIC) != 0 &&
                    (modifiers & MODIFIERS_IGNORE) == 0) {

                Class<?>[] parameterTypes = method.getParameterTypes();
                //parameterTypes 为我们传递的Event的Class类型
                if (parameterTypes.length == 1) {
                    Subscribe annotation = method.getAnnotation(Subscribe.class);

                    if (annotation != null) {
                        Class<?> eventType = parameterTypes[0];

                        //解析的方法、事件类型、线程策略、优先级、是否为粘性事件 都可以得到
                    }

                }
            }

        }
    }



    private FindState prepareFindState() {
        return null;
    }


    /**
     * @desc 内部类
     */

    static class FindState {

        Class<?> clazz;

        public void initForSubscriber(Class<?> subscriberClass) {

        }
    }

}
