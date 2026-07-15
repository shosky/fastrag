package com.fastrag.module.tools.entity;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillShareConfigTest {

    @Test
    void testDefaultConfig() {
        SkillShareConfig config = SkillShareConfig.defaultConfig();
        assertEquals("user", config.getAccessLevel());
        assertTrue(config.getDepartmentIds().isEmpty());
        assertTrue(config.getUserUids().isEmpty());
    }

    @Test
    void testBuiltinConfig() {
        SkillShareConfig config = SkillShareConfig.builtinConfig();
        assertEquals("global", config.getAccessLevel());
        assertTrue(config.getDepartmentIds().isEmpty());
        assertTrue(config.getUserUids().isEmpty());
    }

    @Test
    void testToMap() {
        SkillShareConfig config = new SkillShareConfig();
        config.setAccessLevel("department");
        config.setDepartmentIds(java.util.List.of("dept-1", "dept-2"));
        config.setUserUids(java.util.List.of("user-a"));

        Map<String, Object> map = config.toMap();
        assertEquals("department", map.get("accessLevel"));
        assertEquals(java.util.List.of("dept-1", "dept-2"), map.get("departmentIds"));
        assertEquals(java.util.List.of("user-a"), map.get("userUids"));
    }

    @Test
    void testToMapDefaultValues() {
        SkillShareConfig config = SkillShareConfig.defaultConfig();
        Map<String, Object> map = config.toMap();
        assertEquals("user", map.get("accessLevel"));
        assertTrue(((java.util.List<?>) map.get("departmentIds")).isEmpty());
        assertTrue(((java.util.List<?>) map.get("userUids")).isEmpty());
    }

    @Test
    void testSettersAndGetters() {
        SkillShareConfig config = new SkillShareConfig();
        config.setAccessLevel("global");
        config.setDepartmentIds(java.util.List.of("dept-all"));
        config.setUserUids(java.util.List.of("uid-1", "uid-2"));

        assertEquals("global", config.getAccessLevel());
        assertEquals(1, config.getDepartmentIds().size());
        assertEquals(2, config.getUserUids().size());
        assertTrue(config.getDepartmentIds().contains("dept-all"));
        assertTrue(config.getUserUids().contains("uid-1"));
    }
}
