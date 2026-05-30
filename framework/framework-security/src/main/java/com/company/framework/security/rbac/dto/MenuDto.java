package com.company.framework.security.rbac.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 계층형 메뉴 트리 응답.
 */
public class MenuDto {
    private Long id;
    private String name;
    private String url;
    private String icon;
    private List<MenuDto> children = new ArrayList<>();

    public MenuDto(Long id, String name, String url, String icon) {
        this.id = id; this.name = name; this.url = url; this.icon = icon;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getIcon() { return icon; }
    public List<MenuDto> getChildren() { return children; }
}
