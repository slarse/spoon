import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Test {
    public static List<Element> getMissingElements(DefinitionSet definitionSet) {
        List<Element> missingElements = new ArrayList<>();
        if (definitionSet.getName().contains("factor") && definitionSet.getElements().size() != 8) {
            List<Factor> factors = Arrays.asList(Factor.values());

            for (Factor factor : factors) {
                List<String> collect =
                        definitionSet.getElements().stream().map(Element::getFactorName).collect(Collectors.toList());
                if (!collect.contains(factor.toString())) {
                    Element missingElement = new Element();
                    missingElement.setFactorName(factor.toString());
                    missingElements.add(missingElement);
                    System.out.println("Missing " + factor.toString() + " in " + definitionSet.getName());
                }
            }

        }
        return missingElements;
    }
}

class DefinitionSet {
   public String getName() {
       return "";
   }

   public List<Element> getElements() {
       return null;
   }
}

class Element {
    public void setFactorName(String s) {

    }

    public String getFactorName() {
        return "";
    }
}

enum Factor {
    A, B, C;
}
