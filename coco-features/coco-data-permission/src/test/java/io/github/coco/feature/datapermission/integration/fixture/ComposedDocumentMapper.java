package io.github.coco.feature.datapermission.integration.fixture;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ComposedDocumentMapper extends BaseMapper<ComposedDocument> {

    @Update("UPDATE documents SET title = #{title} WHERE archived = FALSE")
    int renameVisibleDocuments(@Param("title") String title);

    @Delete("DELETE FROM documents WHERE archived = TRUE")
    int deleteArchivedDocuments();

    @Select("""
            SELECT d.id, d.tenant_id, d.department_id, d.title, d.archived
            FROM documents d
            JOIN document_tags t ON t.document_id = d.id
            WHERE t.tag = #{tag} AND d.archived = FALSE
            ORDER BY d.id
            """)
    List<ComposedDocument> selectByTagJoin(@Param("tag") String tag);

    @Select("""
            SELECT d.id, d.tenant_id, d.department_id, d.title, d.archived
            FROM documents d
            WHERE d.archived = FALSE
              AND EXISTS (
                  SELECT 1
                  FROM document_tags t
                  WHERE t.document_id = d.id AND t.tag = #{tag}
              )
            ORDER BY d.id
            """)
    List<ComposedDocument> selectByTagSubquery(@Param("tag") String tag);

    @InterceptorIgnore(tenantLine = "true", dataPermission = "false")
    @Select("""
            SELECT id, tenant_id, department_id, title, archived
            FROM documents
            WHERE archived = FALSE
            ORDER BY id
            """)
    List<ComposedDocument> selectIgnoringTenant();

    @InterceptorIgnore(tenantLine = "true", dataPermission = "false")
    @Select("SELECT COUNT(*) FROM documents")
    long countWithUnlistedTenantIgnore();
}
