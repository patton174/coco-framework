package ${crud.basePackage}.infrastructure.${crud.resourcePackage};

import java.util.Optional;
<#list crud.idTypeImports as typeImport>
import ${typeImport};
</#list>

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import ${crud.basePackage}.domain.${crud.resourcePackage}.${crud.resourceName};
import ${crud.basePackage}.domain.${crud.resourcePackage}.${crud.resourceName}Repository;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus repository adapter for ${crud.resourceName}.
 */
@Repository
public class MybatisPlus${crud.resourceName}Repository implements ${crud.resourceName}Repository {

    private final ${crud.resourceName}Mapper mapper;

    public MybatisPlus${crud.resourceName}Repository(${crud.resourceName}Mapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<${crud.resourceName}> findById(${crud.id.javaType} ${crud.id.name}) {
        return Optional.ofNullable(this.mapper.selectById(${crud.id.name}))
                .map(${crud.resourceName}Entity::toDomain);
    }

    @Override
    public PageResult findPage(long page, long size) {
        IPage<${crud.resourceName}Entity> result = this.mapper.selectPage(Page.of(page, size), null);
        return new PageResult(
                result.getRecords().stream().map(${crud.resourceName}Entity::toDomain).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    @Override
    public ${crud.resourceName} create(${crud.resourceName} ${crud.resourceVariable}) {
        ${crud.resourceName}Entity entity = ${crud.resourceName}Entity.fromDomain(${crud.resourceVariable});
        this.mapper.insert(entity);
        return entity.toDomain();
    }

    @Override
    public ${crud.resourceName} update(${crud.resourceName} ${crud.resourceVariable}) {
        ${crud.resourceName}Entity entity = ${crud.resourceName}Entity.fromDomain(${crud.resourceVariable});
        this.mapper.updateById(entity);
        return entity.toDomain();
    }

    @Override
    public void deleteById(${crud.id.javaType} ${crud.id.name}) {
        this.mapper.deleteById(${crud.id.name});
    }
}
