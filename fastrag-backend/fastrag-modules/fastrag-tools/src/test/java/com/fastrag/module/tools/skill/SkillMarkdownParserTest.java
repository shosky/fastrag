package com.fastrag.module.tools.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillMarkdownParserTest {

    private SkillMarkdownParser parser;

    @BeforeEach
    void setUp() {
        parser = new SkillMarkdownParser();
    }

    @Test
    void testParseValidSkillMd() {
        String content = "---\nname: Web Search\nslug: web-search\ndescription: Search the web\nversion: 1.0.0\ncategory: retrieval\n---\n\n# Web Search\n\nSearch content here.";
        
        SkillMarkdownParser.SkillParseResult result = parser.parse(content);
        
        assertEquals("web-search", result.getSlug());
        assertEquals("Web Search", result.getName());
        assertEquals("Search the web", result.getDescription());
        assertEquals("1.0.0", result.getVersion());
        assertEquals("retrieval", result.getCategory());
        assertNotNull(result.getMetadata());
        assertEquals("Web Search", result.getMetadata().get("name"));
        assertTrue(result.getBody().contains("Search content"));
    }

    @Test
    void testParseMinimalSkillMd() {
        String content = "---\nname: Test\nslug: test\ndescription: A test skill\n---\n\nBody";
        
        SkillMarkdownParser.SkillParseResult result = parser.parse(content);
        
        assertEquals("test", result.getSlug());
        assertEquals("Test", result.getName());
        assertEquals("A test skill", result.getDescription());
    }

    @Test
    void testParseMissingName() {
        String content = "---\ndescription: No name\n---\n\nBody";
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("缺少 name"));
    }

    @Test
    void testParseMissingDescription() {
        String content = "---\nname: Test\n---\n\nBody";
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("缺少 description"));
    }

    @Test
    void testParseNoFrontmatter() {
        String content = "# No frontmatter\n\nJust body";
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("必须以 --- frontmatter 开头"));
    }

    @Test
    void testParseMalformedFrontmatter() {
        // ---extra--- 导致 frontmatter 中只有 name，缺少 description
        String content = "---\nname: Test\n---extra---\ndescription: Bad\n---\n\nBody";
        
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("缺少 description"));
    }

    @Test
    void testParseWithBom() {
        String content = "\uFEFF---\nname: BOM Test\nslug: bom-test\ndescription: With BOM\n---\n\nBody";
        
        SkillMarkdownParser.SkillParseResult result = parser.parse(content);
        
        assertEquals("bom-test", result.getSlug());
        assertEquals("BOM Test", result.getName());
    }

    @Test
    void testParseWithExtraFields() {
        String content = "---\nname: Full Skill\nslug: full-skill\ndescription: Has all fields\nversion: 2.0.0\ncategory: analysis\nicon: \"#ff0000\"\nauthor: test\nenabled: true\n---\n\nFull body content here";
        
        SkillMarkdownParser.SkillParseResult result = parser.parse(content);
        
        assertEquals("full-skill", result.getSlug());
        assertEquals("2.0.0", result.getVersion());
        assertEquals("analysis", result.getCategory());
        assertEquals("#ff0000", result.getIcon());
        assertEquals("test", result.getMetadata().get("author"));
    }

    // ========== M1-2 slug 白名单接线 ==========

    @Test
    void testParseInvalidSlugRejected() {
        String content = "---\nname: Evil\nslug: ../evil\ndescription: e\n---\n\nBody";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("slug"), "消息应包含字段名 slug");
    }

    @Test
    void testParseSlugDerivedFromNameWhenAbsent() {
        String content = "---\nname: My Cool Skill\ndescription: d\n---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertEquals("my-cool-skill", result.getSlug());
    }

    @Test
    void testParseSlugDerivationFailureRejected() {
        String content = "---\nname: 中文名字\ndescription: d\n---\n\nBody";

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("slug"), "消息应包含字段名 slug");
    }

    // ========== M3-2（D9）：frontmatter 依赖三字段解析（tool/skill/mcp_dependencies，兼容 string 与 list） ==========

    @Test
    void testParse_DependencyFrontmatterLists() {
        // 与 builtin-skills/web-search/SKILL.md 实际写法一致
        String content = "---\nname: 联网搜索\nslug: web-search\ndescription: d\n"
            + "tool_dependencies:\n  - tavily_search\n"
            + "skill_dependencies: []\n"
            + "mcp_dependencies: []\n"
            + "---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertEquals(List.of("tavily_search"), result.getToolDependencies(), "tool_dependencies 列表应解析");
        assertTrue(result.getSkillDependencies().isEmpty(), "空 skill_dependencies 应为空列表");
        assertTrue(result.getMcpDependencies().isEmpty(), "空 mcp_dependencies 应为空列表");
    }

    @Test
    void testParse_DependenciesAbsentYieldEmptyLists() {
        String content = "---\nname: Plain\nslug: plain\ndescription: d\n---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertTrue(result.getToolDependencies().isEmpty());
        assertTrue(result.getSkillDependencies().isEmpty());
        assertTrue(result.getMcpDependencies().isEmpty());
    }

    @Test
    void testParse_DependencyStringFormNormalizedToList() {
        String content = "---\nname: StrDeps\nslug: str-deps\ndescription: d\n"
            + "tool_dependencies: tavily_search\n"
            + "mcp_dependencies: filesystem\n"
            + "---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertEquals(List.of("tavily_search"), result.getToolDependencies(), "string 形态应归一为单元素列表");
        assertEquals(List.of("filesystem"), result.getMcpDependencies(), "string 形态应归一为单元素列表");
    }

    @Test
    void testParse_AllThreeDependencyTypesMultiValue() {
        String content = "---\nname: Multi\nslug: multi\ndescription: d\n"
            + "tool_dependencies:\n  - t1\n  - t2\n"
            + "skill_dependencies:\n  - other-skill\n"
            + "mcp_dependencies:\n  - github\n---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertEquals(List.of("t1", "t2"), result.getToolDependencies());
        assertEquals(List.of("other-skill"), result.getSkillDependencies());
        assertEquals(List.of("github"), result.getMcpDependencies());
    }

    // ========== F4 运行期凭据通道：frontmatter env 声明解析（skill-feature-batch.md F4，2026-10-07） ==========

    @Test
    void parse_EnvFrontmatterListForm_YieldsVariableNameList() {
        // list 写法：env: 下逐行列变量名
        String content = "---\nname: Cred\nslug: cred\ndescription: d\n"
            + "env:\n  - QQQ_API_KEY\n  - QQQ_API_SECRET\n"
            + "---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertEquals(List.of("QQQ_API_KEY", "QQQ_API_SECRET"), result.getEnvs(),
            "env list 形态应解析为变量名列表");
    }

    @Test
    void parse_EnvStringForm_NormalizedToSingleElementList() {
        // string 写法：单变量名（与依赖三字段同款容忍）
        String content = "---\nname: Cred1\nslug: cred-1\ndescription: d\n"
            + "env: SINGLE_TOKEN\n"
            + "---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertEquals(List.of("SINGLE_TOKEN"), result.getEnvs(),
            "env string 形态应归一为单元素列表");
    }

    @Test
    void parse_EnvAbsent_YieldsEmptyList() {
        String content = "---\nname: Plain\nslug: plain\ndescription: d\n---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertNotNull(result.getEnvs(), "缺省 env 应为空列表而非 null");
        assertTrue(result.getEnvs().isEmpty(), "缺省 env 应为空列表");
    }

    @Test
    void parse_EnvEmptyListAndBlankEntries_Filtered() {
        String content = "---\nname: Cred2\nslug: cred-2\ndescription: d\n"
            + "env:\n  - KEEP_VAR\n  - \"\"\n  -   \n---\n\nBody";

        SkillMarkdownParser.SkillParseResult result = parser.parse(content);

        assertEquals(List.of("KEEP_VAR"), result.getEnvs(),
            "空串/空白项应过滤，仅保留有效变量名");
    }
}
