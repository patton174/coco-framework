package ${crud.basePackage}.interfaces.rest.${crud.resourcePackage};

<#list crud.idTypeImports as typeImport>
import ${typeImport};
</#list>

import ${crud.basePackage}.application.${crud.resourcePackage}.${crud.resourceName}ApplicationService;
import ${crud.basePackage}.domain.${crud.resourcePackage}.${crud.resourceName};
import ${crud.basePackage}.interfaces.rest.${crud.resourcePackage}.dto.Create${crud.resourceName}Request;
import ${crud.basePackage}.interfaces.rest.${crud.resourcePackage}.dto.${crud.resourceName}Response;
import ${crud.basePackage}.interfaces.rest.${crud.resourcePackage}.dto.Update${crud.resourceName}Request;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for ${crud.resourceName}.
 */
@RestController
@RequestMapping("${crud.apiPath}")
public class ${crud.resourceName}Controller {

    private final ${crud.resourceName}ApplicationService applicationService;

    public ${crud.resourceName}Controller(${crud.resourceName}ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{${crud.id.name}}")
    public ${crud.resourceName}Response get(@PathVariable("${crud.id.name}") ${crud.id.javaType} ${crud.id.name}) {
        return ${crud.resourceName}Response.from(this.applicationService.get(${crud.id.name}));
    }

    @GetMapping
    public ${crud.resourceName}Response.Page list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return ${crud.resourceName}Response.Page.from(this.applicationService.list(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ${crud.resourceName}Response create(@Valid @RequestBody Create${crud.resourceName}Request request) {
        ${crud.resourceName} created = this.applicationService.create(new ${crud.resourceName}(
<#if crud.id.input>
                request.${crud.id.name}(),
<#else>
                null,
</#if>
<#list crud.fields as field>
                request.${field.name}()<#if field_has_next>,</#if>
</#list>
        ));
        return ${crud.resourceName}Response.from(created);
    }

    @PutMapping("/{${crud.id.name}}")
    public ${crud.resourceName}Response update(
            @PathVariable("${crud.id.name}") ${crud.id.javaType} ${crud.id.name},
            @Valid @RequestBody Update${crud.resourceName}Request request) {
        ${crud.resourceName} updated = this.applicationService.update(new ${crud.resourceName}(
                ${crud.id.name},
<#list crud.fields as field>
                request.${field.name}()<#if field_has_next>,</#if>
</#list>
        ));
        return ${crud.resourceName}Response.from(updated);
    }

    @DeleteMapping("/{${crud.id.name}}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("${crud.id.name}") ${crud.id.javaType} ${crud.id.name}) {
        this.applicationService.delete(${crud.id.name});
    }
}
