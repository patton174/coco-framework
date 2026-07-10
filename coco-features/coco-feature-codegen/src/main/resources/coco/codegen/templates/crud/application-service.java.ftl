package ${crud.basePackage}.application.${crud.resourcePackage};

<#list crud.idTypeImports as typeImport>
import ${typeImport};
</#list>

import io.github.coco.common.exception.CocoCommonErrorCode;
import ${crud.basePackage}.domain.${crud.resourcePackage}.${crud.resourceName};
import ${crud.basePackage}.domain.${crud.resourcePackage}.${crud.resourceName}Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for ${crud.resourceName} use cases.
 */
@Service
public class ${crud.resourceName}ApplicationService {

    private final ${crud.resourceName}Repository repository;

    public ${crud.resourceName}ApplicationService(${crud.resourceName}Repository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ${crud.resourceName} get(${crud.id.javaType} ${crud.id.name}) {
        return this.repository.findById(${crud.id.name})
                .orElseThrow(() -> CocoCommonErrorCode.NOT_FOUND.notFound(
                        "${crud.resourceName}:" + ${crud.id.name}));
    }

    @Transactional(readOnly = true)
    public ${crud.resourceName}Repository.PageResult list(long page, long size) {
        if (page < 1 || size < 1 || size > 100) {
            throw CocoCommonErrorCode.INVALID_ARGUMENT.request("page/size");
        }
        return this.repository.findPage(page, size);
    }

    @Transactional
    public ${crud.resourceName} create(${crud.resourceName} ${crud.resourceVariable}) {
        return this.repository.create(${crud.resourceVariable});
    }

    @Transactional
    public ${crud.resourceName} update(${crud.resourceName} ${crud.resourceVariable}) {
        get(${crud.resourceVariable}.${crud.id.name}());
        return this.repository.update(${crud.resourceVariable});
    }

    @Transactional
    public void delete(${crud.id.javaType} ${crud.id.name}) {
        get(${crud.id.name});
        this.repository.deleteById(${crud.id.name});
    }
}
