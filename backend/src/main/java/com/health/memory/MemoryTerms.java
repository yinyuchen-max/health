package com.health.memory;

import java.util.*;
import java.util.regex.Pattern;

/** Deterministic lexical fallback; entities are cues, never inferred medical relations. */
public final class MemoryTerms {
    private MemoryTerms() {}
    private static final List<Set<String>> GROUPS=List.of(
            Set.of("运动","锻炼","健身","exercise","workout"), Set.of("减重","减肥","减脂","体重","weight"),
            Set.of("饮食","吃","食物","diet","food"), Set.of("睡眠","睡觉","失眠","sleep"),
            Set.of("膝盖","膝关节","knee"),Set.of("花生","peanut"),Set.of("过敏","allergy"),
            Set.of("游泳","swim"),Set.of("跑步","跑","run"),Set.of("血压","高血压","pressure"),
            Set.of("血糖","糖尿病","glucose"),Set.of("清淡","低盐"),Set.of("目标","计划","goal"));
    public static Set<String> entities(String text) {
        String value=text==null?"":text.toLowerCase(Locale.ROOT);
        Set<String> result=new LinkedHashSet<>();
        for(var group:GROUPS) if(group.stream().anyMatch(value::contains)) result.add(group.stream().sorted().findFirst().orElseThrow());
        return result;
    }
    public static Set<String> tokens(String text) {
        Set<String> result=new LinkedHashSet<>(entities(text));
        var matcher=Pattern.compile("[a-z0-9_]+|[\\p{IsHan}]+").matcher(text==null?"":text.toLowerCase(Locale.ROOT));
        while(matcher.find()) {
            String word=matcher.group();
            if(word.codePoints().allMatch(cp->Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN)) {
                for(int i=0;i<word.length()-1;i++) result.add(word.substring(i,i+2));
            } else if(word.length()>1) result.add(word);
        }
        return result;
    }
    public static double similarity(String a,String b) {
        var left=tokens(a); var right=tokens(b);
        if(left.isEmpty()||right.isEmpty()) return 0;
        long matches=left.stream().filter(right::contains).count();
        return Math.min(1, matches / Math.sqrt((double)left.size()*right.size()));
    }
}
