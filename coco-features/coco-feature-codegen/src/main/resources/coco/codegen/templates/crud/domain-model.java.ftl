package ${crud.basePackage}.domain.${crud.resourcePackage};

<#list crud.typeImports as typeImport>
import ${typeImport};
</#list>
/**
 * Domain model for ${crud.resourceName}.
 */
public record ${crud.resourceName}(
<#list crud.allFields as field>
        ${field.javaType} ${field.name}<#if field_has_next>,</#if>
</#list>
) {
}
