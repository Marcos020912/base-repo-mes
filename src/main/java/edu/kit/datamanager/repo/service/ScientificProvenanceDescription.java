package edu.kit.datamanager.repo.service;
import edu.kit.datamanager.repo.domain.ScientificRecord;
import java.util.ArrayList;
/** Author assertions, deliberately not inferred execution activities or timestamps. */
public final class ScientificProvenanceDescription {
    private ScientificProvenanceDescription(){}
    public static String describe(ScientificRecord record){
        var parts=new ArrayList<String>();
        add(parts,"Producción y origen",record.getProductionDescription());add(parts,"Procesamiento",record.getProcessingDescription());add(parts,"Herramientas y versiones",record.getProcessingTools());
        return parts.isEmpty()?null:"Procedencia científica declarada por el depositante; no es una ejecución verificada por la plataforma.\n"+String.join("\n",parts);
    }
    private static void add(java.util.List<String> parts,String label,String value){if(value!=null&&!value.isBlank())parts.add(label+": "+value);}
}
