package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.skill.Skill;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillI18nUtilTest {

    @Test
    void zhCn_parsesExpectedFields() {
        String i18n = """
                {
                  "zh-CN": {
                    "displayName": "中文名",
                    "oneLine": "一句话用途",
                    "scenarios": ["场景1", "场景2", "场景3"],
                    "inputChecklist": ["信息1", "信息2"],
                    "examples": [
                      {"title": "示例A", "input": "输入A", "expectedOutput": "输出A"},
                      {"title": "示例B", "input": "输入B", "expectedOutput": "输出B"}
                    ],
                    "outputFormat": "Markdown"
                  }
                }
                """;

        Skill skill = new Skill(
                "id",
                "RawName",
                "RawDesc",
                null,
                "cat",
                true,
                "guide",
                List.of(),
                i18n,
                "external",
                "en",
                "basic",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                "prompt",
                SkillExecutionMode.SINGLE
        );

        SkillI18nZhCn zh = SkillI18nUtil.zhCn(skill);
        assertNotNull(zh);
        assertEquals("中文名", zh.displayName());
        assertEquals("一句话用途", zh.oneLine());
        assertEquals(3, zh.scenarios().size());
        assertEquals(2, zh.inputChecklist().size());
        assertEquals(2, zh.examples().size());
        assertEquals("示例A", zh.examples().get(0).title());
        assertEquals("输入A", zh.examples().get(0).input());
        assertEquals("输出A", zh.examples().get(0).expectedOutput());
        assertEquals("Markdown", zh.outputFormat());
    }
}

