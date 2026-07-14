package io.github.coco.feature.datapermission.integration.fixture;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("documents")
public class ComposedDocument {

    @TableId
    private Long id;

    private String tenantId;

    private Long departmentId;

    private String title;

    private Boolean archived;

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return this.tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Long getDepartmentId() {
        return this.departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getArchived() {
        return this.archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }
}
