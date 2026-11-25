package com.zc.agent.core.modules.runtime.plugin;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runtime Plugin 使用的轻量 SemVer 版本约束匹配器。 */
public class RuntimePluginVersionMatcher {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)"
                    + "(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    private static final Pattern COMPARATOR_PATTERN = Pattern.compile("^(>=|<=|>|<|=)?(.+)$");
    private static final Pattern WILDCARD_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)(?:\\.(0|[1-9]\\d*))?\\.(?:x|X|\\*)$");

    /**
     * 判断插件版本是否满足约束。
     * 支持精确版本、`^`、`~`、比较区间、`x/*` 通配符和 `||`。
     *
     * @param version    已安装插件 SemVer 版本
     * @param constraint Manifest 版本约束
     * @return 是否满足
     */
    public boolean matches(String version, String constraint) {
        SemanticVersion candidate = SemanticVersion.parse(version);
        if (constraint == null || constraint.isBlank()) {
            throw new IllegalArgumentException("版本约束不能为空");
        }
        String[] alternatives = constraint.trim().split("\\s*\\|\\|\\s*", -1);
        if (Arrays.stream(alternatives).anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("无效版本约束: " + constraint);
        }
        boolean matched = false;
        for (String alternative : alternatives) {
            boolean alternativeMatched = matchesAll(candidate, alternative);
            matched = matched || alternativeMatched;
        }
        return matched;
    }

    private boolean matchesAll(SemanticVersion candidate, String constraint) {
        if (constraint.isBlank() || "*".equals(constraint)) {
            return true;
        }
        if (constraint.startsWith("^") && !constraint.contains(" ")) {
            SemanticVersion minimum = SemanticVersion.parse(constraint.substring(1));
            return candidate.compareTo(minimum) >= 0 && candidate.compareTo(caretUpperBound(minimum)) < 0;
        }
        if (constraint.startsWith("~") && !constraint.contains(" ")) {
            SemanticVersion minimum = SemanticVersion.parse(constraint.substring(1));
            return candidate.compareTo(minimum) >= 0 && candidate.compareTo(tildeUpperBound(minimum)) < 0;
        }
        Matcher wildcard = WILDCARD_PATTERN.matcher(constraint);
        if (wildcard.matches()) {
            BigInteger major = new BigInteger(wildcard.group(1));
            String minorValue = wildcard.group(2);
            SemanticVersion minimum = new SemanticVersion(
                    major, minorValue == null ? BigInteger.ZERO : new BigInteger(minorValue),
                    BigInteger.ZERO, List.of());
            SemanticVersion maximum = minorValue == null
                    ? new SemanticVersion(major.add(BigInteger.ONE), BigInteger.ZERO, BigInteger.ZERO, List.of())
                    : new SemanticVersion(major, new BigInteger(minorValue).add(BigInteger.ONE),
                            BigInteger.ZERO, List.of());
            return candidate.compareTo(minimum) >= 0 && candidate.compareTo(maximum) < 0;
        }
        String[] tokens = constraint.split("\\s+");
        boolean allMatched = true;
        for (String token : tokens) {
            Matcher comparator = COMPARATOR_PATTERN.matcher(token);
            if (!comparator.matches()) {
                throw new IllegalArgumentException("不支持的版本约束: " + constraint);
            }
            String operator = comparator.group(1) == null ? "=" : comparator.group(1);
            SemanticVersion boundary = SemanticVersion.parse(comparator.group(2));
            int compared = candidate.compareTo(boundary);
            boolean matched = switch (operator) {
                case ">" -> compared > 0;
                case ">=" -> compared >= 0;
                case "<" -> compared < 0;
                case "<=" -> compared <= 0;
                case "=" -> compared == 0;
                default -> false;
            };
            allMatched = allMatched && matched;
        }
        return allMatched;
    }

    private SemanticVersion caretUpperBound(SemanticVersion version) {
        if (version.major().signum() > 0) {
            return new SemanticVersion(
                    version.major().add(BigInteger.ONE), BigInteger.ZERO, BigInteger.ZERO, List.of());
        }
        if (version.minor().signum() > 0) {
            return new SemanticVersion(
                    BigInteger.ZERO, version.minor().add(BigInteger.ONE), BigInteger.ZERO, List.of());
        }
        return new SemanticVersion(
                BigInteger.ZERO, BigInteger.ZERO, version.patch().add(BigInteger.ONE), List.of());
    }

    private SemanticVersion tildeUpperBound(SemanticVersion version) {
        return new SemanticVersion(
                version.major(), version.minor().add(BigInteger.ONE), BigInteger.ZERO, List.of());
    }

    private record SemanticVersion(BigInteger major,
                                   BigInteger minor,
                                   BigInteger patch,
                                   List<String> prerelease) implements Comparable<SemanticVersion> {

        private static SemanticVersion parse(String value) {
            if (value == null) {
                throw new IllegalArgumentException("SemVer 版本不能为空");
            }
            Matcher matcher = VERSION_PATTERN.matcher(value.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("无效 SemVer 版本: " + value);
            }
            return new SemanticVersion(
                    new BigInteger(matcher.group(1)),
                    new BigInteger(matcher.group(2)),
                    new BigInteger(matcher.group(3)),
                    matcher.group(4) == null ? List.of() : List.of(matcher.group(4).split("\\."))
            );
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int compared = major.compareTo(other.major);
            if (compared != 0) {
                return compared;
            }
            compared = minor.compareTo(other.minor);
            if (compared != 0) {
                return compared;
            }
            compared = patch.compareTo(other.patch);
            if (compared != 0) {
                return compared;
            }
            if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
                if (prerelease.isEmpty() && other.prerelease.isEmpty()) {
                    return 0;
                }
                return prerelease.isEmpty() ? 1 : -1;
            }
            for (int index = 0; index < Math.min(prerelease.size(), other.prerelease.size()); index++) {
                compared = compareIdentifier(prerelease.get(index), other.prerelease.get(index));
                if (compared != 0) {
                    return compared;
                }
            }
            return Integer.compare(prerelease.size(), other.prerelease.size());
        }

        private int compareIdentifier(String left, String right) {
            boolean leftNumeric = left.chars().allMatch(Character::isDigit);
            boolean rightNumeric = right.chars().allMatch(Character::isDigit);
            if (leftNumeric && rightNumeric) {
                return new BigInteger(left).compareTo(new BigInteger(right));
            }
            if (leftNumeric != rightNumeric) {
                return leftNumeric ? -1 : 1;
            }
            return left.compareTo(right);
        }
    }
}
