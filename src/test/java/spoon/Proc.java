package spoon;


import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.QueueProcessingManager;

public class Proc extends AbstractProcessor<CtInvocation<?>> {

    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("Test.java");

        QueueProcessingManager processingManager = new QueueProcessingManager(launcher.getFactory());
        processingManager.addProcessor(new Proc());
        processingManager.process(launcher.buildModel().getAllTypes().stream().filter(type -> type.getSimpleName().equals("Test")).findFirst().get());
    }

    @Override
    public boolean isToBeProcessed(CtInvocation<?> candidate) {
        for (CtTypeReference type : candidate.getReferencedTypes()) {
            if (type.isEnum() && candidate.getExecutable().getSimpleName().equalsIgnoreCase("toString")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void process(CtInvocation<?> element) {
        System.out
                .println("Invocation element: " + element + " , " + element.getType() + " , " + element.getPosition().toString());

        System.out.println("----------------------------------------------------------------------------------------------------------");

        System.out.println(
                element.getExecutable().getDeclaringType().getSimpleName() + " , "
                        + element.getParent()
                        + " , "
                        + (element.getParent().getParent() != null ? element.getParent().getParent() : "null"));

        System.out.println("----------------------------------------------------------------------------------------------------------");

        element.getReferencedTypes().forEach(System.out::println);

        System.out.println("--------------------------------------------------END--------------------------------------------------------");

    }

}