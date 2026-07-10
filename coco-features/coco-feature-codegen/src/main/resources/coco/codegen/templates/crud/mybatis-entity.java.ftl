package ${crud.basePackage}.infrastructure.${crud.resourcePackage};

<#list crud.typeImports as typeImport>
import ${typeImport};
</#list>

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ${crud.basePackage}.domain.${crud.resourcePackage}.${crud.resourceName};

/**
 * MyBatis-Plus persistence entity for ${crud.resourceName}.
 */
@TableName("${crud.tableName}")
public class ${crud.resourceName}Entity {

    @TableId(value = "${crud.id.columnName}", type = IdType.${crud.id.strategy})
    private ${crud.id.javaType} ${crud.id.name};

<#list crud.fields as field>
    @TableField("${field.columnName}")
    private ${field.javaType} ${field.name};

</#list>
    public ${crud.id.javaType} get${crud.id.name?cap_first}() {
        return this.${crud.id.name};
    }

    public void set${crud.id.name?cap_first}(${crud.id.javaType} ${crud.id.name}) {
        this.${crud.id.name} = ${crud.id.name};
    }

<#list crud.fields as field>
    public ${field.javaType} get${field.name?cap_first}() {
        return this.${field.name};
    }

    public void set${field.name?cap_first}(${field.javaType} ${field.name}) {
        this.${field.name} = ${field.name};
    }

</#list>
    ${crud.resourceName} toDomain() {
        return new ${crud.resourceName}(
                this.${crud.id.name},
<#list crud.fields as field>
                this.${field.name}<#if field_has_next>,</#if>
</#list>
        );
    }

    static ${crud.resourceName}Entity fromDomain(${crud.resourceName} source) {
        ${crud.resourceName}Entity entity = new ${crud.resourceName}Entity();
        entity.set${crud.id.name?cap_first}(source.${crud.id.name}());
<#list crud.fields as field>
        entity.set${field.name?cap_first}(source.${field.name}());
</#list>
        return entity;
    }
}
