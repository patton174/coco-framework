package ${crud.basePackage}.domain.${crud.resourcePackage};

import java.util.List;
import java.util.Optional;
<#list crud.idTypeImports as typeImport>
import ${typeImport};
</#list>

/**
 * Persistence contract for ${crud.resourceName}.
 */
public interface ${crud.resourceName}Repository {

    Optional<${crud.resourceName}> findById(${crud.id.javaType} ${crud.id.name});

    PageResult findPage(long page, long size);

    ${crud.resourceName} create(${crud.resourceName} ${crud.resourceVariable});

    ${crud.resourceName} update(${crud.resourceName} ${crud.resourceVariable});

    void deleteById(${crud.id.javaType} ${crud.id.name});

    record PageResult(List<${crud.resourceName}> items, long total, long page, long size) {

        public PageResult {
            items = List.copyOf(items);
        }
    }
}
