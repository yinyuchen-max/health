package com.health.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

/** 一次加载并校验科室字典；可通过 file: 资源替换，重启生效。 */
@Service
public class DepartmentCatalog {
    private final List<String> names;
    private final Map<String, String> aliases;

    public DepartmentCatalog(@Value("${medical.departments.resource:classpath:catalogs/departments.yml}") Resource resource) {
        YamlMapFactoryBean factory = new YamlMapFactoryBean();
        factory.setResources(resource);
        Map<String, Object> yaml = factory.getObject();
        if (yaml == null || !(yaml.get("names") instanceof List<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("科室字典 names 必须是非空列表");
        }
        List<String> loadedNames = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String name) || name.isBlank() || !name.equals(name.trim()) || loadedNames.contains(name)) {
                throw new IllegalArgumentException("科室名称必须非空、唯一且无首尾空白");
            }
            loadedNames.add(name);
        }
        names = List.copyOf(loadedNames);
        Map<String, String> loadedAliases = new LinkedHashMap<>();
        Object rawAliases = yaml.get("aliases");
        if (rawAliases != null) {
            if (!(rawAliases instanceof Map<?, ?> map)) throw new IllegalArgumentException("科室 aliases 必须是映射");
            map.forEach((key, value) -> {
                if (!(key instanceof String alias) || alias.isBlank() || !alias.equals(alias.trim())
                        || !(value instanceof String target) || !names.contains(target)
                        || (names.contains(alias) && !alias.equals(target))) {
                    throw new IllegalArgumentException("科室别名必须指向已有科室，且不得覆盖其他正式科室");
                }
                loadedAliases.put(alias, target);
            });
        }
        aliases = Map.copyOf(loadedAliases);
    }

    public List<String> names() { return names; }
    public String normalize(String value) {
        if (value == null) return null;
        String name = value.trim();
        return names.contains(name) ? name : aliases.get(name);
    }
    public boolean mentionedIn(String message) {
        return names.stream().anyMatch(message::contains) || aliases.keySet().stream().anyMatch(message::contains);
    }
}
