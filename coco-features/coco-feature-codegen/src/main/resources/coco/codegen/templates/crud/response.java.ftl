package ${crud.basePackage}.interfaces.rest.${crud.resourcePackage}.dto;

import java.util.List;
<#list crud.typeImports as typeImport>
import ${typeImport};
</#list>

import ${crud.basePackage}.domain.${crud.resourcePackage}.${crud.resourceName};
import ${crud.basePackage}.domain.${crud.resourcePackage}.${crud.resourceName}Repository;

/**
 * Response body for ${crud.resourceName}.
 */
public record ${crud.resourceName}Response(
<#list crud.allFields as field>
        ${field.javaType} ${field.name}<#if field_has_next>,</#if>
</#list>
) {

    public static ${crud.resourceName}Response from(${crud.resourceName} source) {
        return new ${crud.resourceName}Response(
<#list crud.allFields as field>
                source.${field.name}()<#if field_has_next>,</#if>
</#list>
        );
    }

    public record Page(List<${crud.resourceName}Response> items, long total, long page, long size) {

        public Page {
            items = List.copyOf(items);
        }

        public static Page from(${crud.resourceName}Repository.PageResult source) {
            return new Page(
                    source.items().stream().map(${crud.resourceName}Response::from).toList(),
                    source.total(),
                    source.page(),
                    source.size());
        }
    }
}
