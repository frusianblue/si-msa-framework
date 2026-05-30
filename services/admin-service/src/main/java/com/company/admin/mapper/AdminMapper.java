package com.company.admin.mapper;

import com.company.admin.domain.MenuRow;
import com.company.admin.domain.ResourceRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMapper {
    // 리소스(URL-권한)
    List<ResourceRow> listResources();

    int insertResource(ResourceRow row);

    int updateResource(ResourceRow row);

    int deleteResource(@Param("id") Long id);

    // 메뉴
    List<MenuRow> listMenus();

    int insertMenu(MenuRow row);

    int updateMenu(MenuRow row);

    int deleteMenu(@Param("id") Long id);

    // 역할 매핑
    List<Map<String, Object>> listRoles();

    int mapRoleResource(@Param("roleId") Long roleId, @Param("resourceId") Long resourceId);

    int unmapRoleResource(@Param("roleId") Long roleId, @Param("resourceId") Long resourceId);

    int mapRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    int unmapRoleMenu(@Param("roleId") Long roleId, @Param("menuId") Long menuId);
}
