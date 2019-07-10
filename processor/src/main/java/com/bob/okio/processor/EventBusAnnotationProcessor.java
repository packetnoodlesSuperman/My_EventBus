package com.bob.okio.processor;

import java.io.BufferedWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Created by xhb on 2019/6/30.
 */

public class EventBusAnnotationProcessor extends AbstractProcessor {

    private boolean verbose;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {

        Messager messager = this.processingEnv.getMessager();

        try {
            String index = (String) this.processingEnv.getOptions().get("eventBusIndex");

            if (index == null) {
                messager.printMessage(Diagnostic.Kind.ERROR, "No option eventBusIndex ");
                return false;
            }

            this.verbose = Boolean.parseBoolean((String) this.processingEnv.getOptions().get("verbose"));

            int lastPeriod = index.lastIndexOf(46);

            this.collectSubscribers(annotations, env, messager);

        }

        return false;
    }

    private void collectSubscribers(Set<? extends TypeElement> annotations, RoundEnvironment env, Messager messager) {
        Iterator<? extends TypeElement> iterator = annotations.iterator();

        while (iterator.hasNext()) {

            TypeElement annotation = (TypeElement) iterator.next();
            Set<? extends Element> elements = env.getElementsAnnotatedWith(annotation);
            Iterator<? extends Element> elementIterator = elements.iterator();

            while (elementIterator.hasNext()) {
                Element element = (Element) elementIterator.next();
                if (element instanceof ExecutableElement) {
                    ExecutableElement method = (ExecutableElement) element;

                }
            }

        }

    }

    private boolean checkHasNoErrors(ExecutableElement element, Messager messager) {
        if (element.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must not be static", element);
            return false;
        } else if (!element.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must be public", element);
            return false;
        } else {
            List<? extends VariableElement> parameters = element.getParameters();
            if (parameters.size() != 1) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Subscriber method must hava exactly 1 parameter", element);
                return false;
            } else {
                return true;
            }
        }
    }

    private void createInfoIndexFile(String index) {
        BufferedWriter writer = null;

        try {
            JavaFileObject sourceFile = this.processingEnv.getFiler().createSourceFile(index, new Element[0]);
        } catch ()
    }
}
