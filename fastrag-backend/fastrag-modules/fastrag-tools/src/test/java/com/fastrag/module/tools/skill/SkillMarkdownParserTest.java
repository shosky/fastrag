package com.fastrag.module.tools.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
