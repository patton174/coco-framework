package ${crud.basePackage}.interfaces.rest.${crud.resourcePackage}.dto;

<#list crud.createTypeImports as typeImport>
import ${typeImport};
</#list>
<#if crud.createNeedsNotBlank>
import jakarta.validation.constraints.NotBlank;
</#if>
<#if crud.createNeedsNotNull>
import jakarta.validation.constraints.NotNull;
</#if>

/**
 * Request body for creating ${crud.resourceName}.
 */
public record Create${crud.resourceName}Request(
<#if crud.id.input>
        @NotNull ${crud.id.javaType} ${crud.id.name},
</#if>
<#list crud.fields as field>
        <#if field.required && !field.primitive><#if field.stringType>@NotBlank <#else>@NotNull </#if></#if>${field.javaType} ${field.name}<#if field_has_next>,</#if>
</#list>
) {
}
