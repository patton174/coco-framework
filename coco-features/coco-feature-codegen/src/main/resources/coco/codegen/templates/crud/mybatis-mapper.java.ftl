package ${crud.basePackage}.infrastructure.${crud.resourcePackage};

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for ${crud.resourceName}.
 */
@Mapper
public interface ${crud.resourceName}Mapper extends BaseMapper<${crud.resourceName}Entity> {
}
