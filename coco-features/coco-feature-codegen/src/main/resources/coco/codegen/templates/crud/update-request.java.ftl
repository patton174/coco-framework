package ${crud.basePackage}.interfaces.rest.${crud.resourcePackage}.dto;

<#list crud.fieldTypeImports as typeImport>
import ${typeImport};
</#list>
<#if crud.updateNeedsNotBlank>
import jakarta.validation.constraints.NotBlank;
</#if>
<#if crud.updateNeedsNotNull>
import jakarta.validation.constraints.NotNull;
</#if>

/**
 * Request body for updating ${crud.resourceName}.
 */
public record Update${crud.resourceName}Request(
<#list crud.fields as field>
        <#if field.required && !field.primitive><#if field.stringType>@NotBlank <#else>@NotNull </#if></#if>${field.javaType} ${field.name}<#if field_has_next>,</#if>
</#list>
) {
}
