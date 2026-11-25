package com.codepath.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.codepath.questions.Concept;
import com.codepath.questions.ConceptRepository;
import com.codepath.questions.Difficulty;
import com.codepath.questions.Question;
import com.codepath.questions.QuestionRepository;
import com.codepath.questions.TestCase;
import com.codepath.questions.TestCaseRepository;
import com.codepath.questions.Topic;
import com.codepath.questions.TopicPrerequisite;
import com.codepath.questions.TopicPrerequisiteRepository;
import com.codepath.questions.TopicRepository;

/**
 * Seeds the question bank on first start: 5 topics with prerequisites, 10 concepts, 15 Java problems
 * (stdin/stdout, public + hidden test cases) and 10 diagnostic questions.
 *
 * <p>Diagnostic questions have no table of their own in the blueprint schema, so they are stored as
 * {@code questions} rows tagged {@value #DIAGNOSTIC_TAG}. Their options are listed in the description
 * as lines {@code A. ...} to {@code D. ...}, and the correct letter is kept in a HIDDEN test case
 * (the answer key must never reach the client). The question bank API must exclude this tag.
 *
 * <p>Seeding is skipped when topics already exist, and can be disabled with
 * {@code codepath.seed.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "codepath.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    public static final String DIAGNOSTIC_TAG = "DIAGNOSTIC";

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final int DEFAULT_TIME_LIMIT_MS = 2000;
    private static final int DEFAULT_MEMORY_LIMIT_KB = 262144;
    private static final String DIAGNOSTIC_INPUT = "N/A";

    private final TopicRepository topicRepository;
    private final TopicPrerequisiteRepository topicPrerequisiteRepository;
    private final ConceptRepository conceptRepository;
    private final QuestionRepository questionRepository;
    private final TestCaseRepository testCaseRepository;

    public DataSeeder(TopicRepository topicRepository, TopicPrerequisiteRepository topicPrerequisiteRepository,
            ConceptRepository conceptRepository, QuestionRepository questionRepository,
            TestCaseRepository testCaseRepository) {
        this.topicRepository = topicRepository;
        this.topicPrerequisiteRepository = topicPrerequisiteRepository;
        this.conceptRepository = conceptRepository;
        this.questionRepository = questionRepository;
        this.testCaseRepository = testCaseRepository;
    }

    private record Case(String input, String expectedOutput, boolean hidden) {
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (topicRepository.count() > 0) {
            log.info("Seed data skipped: topics already exist");
            return;
        }
        Map<String, Topic> topics = seedTopics();
        seedPrerequisites(topics);
        Map<String, Concept> concepts = seedConcepts(topics);
        seedArrays(topics, concepts);
        seedStrings(topics, concepts);
        seedBinarySearch(topics, concepts);
        seedStacks(topics, concepts);
        seedDynamicProgramming(topics, concepts);
        seedDiagnosticQuestions(topics, concepts);
        log.info("Seeded {} topics, {} concepts, {} questions and {} test cases",
                topicRepository.count(), conceptRepository.count(), questionRepository.count(),
                testCaseRepository.count());
    }

    private Map<String, Topic> seedTopics() {
        Map<String, Topic> topics = new LinkedHashMap<>();
        saveTopic(topics, "Arrays", "Contiguous collections: traversal, two pointers, prefix sums and sliding windows.", 1);
        saveTopic(topics, "Strings", "Character processing: frequency counting, palindromes and pattern checks.", 2);
        saveTopic(topics, "Binary Search", "Halving the search space on sorted data or on a monotonic answer range.", 3);
        saveTopic(topics, "Stacks", "LIFO structures for matching, monotonic ordering and range queries.", 4);
        saveTopic(topics, "Dynamic Programming", "Optimal substructure and overlapping subproblems: 1D and 2D tables.", 5);
        return topics;
    }

    private void seedPrerequisites(Map<String, Topic> topics) {
        linkPrerequisite(topics, "Strings", "Arrays");
        linkPrerequisite(topics, "Binary Search", "Arrays");
        linkPrerequisite(topics, "Stacks", "Arrays");
        linkPrerequisite(topics, "Stacks", "Strings");
        linkPrerequisite(topics, "Dynamic Programming", "Arrays");
        linkPrerequisite(topics, "Dynamic Programming", "Strings");
    }

    private Map<String, Concept> seedConcepts(Map<String, Topic> topics) {
        Map<String, Concept> concepts = new LinkedHashMap<>();
        saveConcept(topics, concepts, "Arrays", "Two Pointers");
        saveConcept(topics, concepts, "Arrays", "Prefix Sum");
        saveConcept(topics, concepts, "Arrays", "Sliding Window");
        saveConcept(topics, concepts, "Strings", "Frequency Counting");
        saveConcept(topics, concepts, "Strings", "Palindromes");
        saveConcept(topics, concepts, "Binary Search", "Search on Sorted Array");
        saveConcept(topics, concepts, "Binary Search", "Search on Answer");
        saveConcept(topics, concepts, "Stacks", "Stack Applications");
        saveConcept(topics, concepts, "Dynamic Programming", "1D Dynamic Programming");
        saveConcept(topics, concepts, "Dynamic Programming", "2D Dynamic Programming");
        return concepts;
    }

    private Topic saveTopic(Map<String, Topic> topics, String name, String description, int displayOrder) {
        Topic topic = new Topic();
        topic.setName(name);
        topic.setDescription(description);
        topic.setDisplayOrder(displayOrder);
        Topic saved = topicRepository.save(topic);
        topics.put(name, saved);
        return saved;
    }

    private void linkPrerequisite(Map<String, Topic> topics, String topic, String prerequisite) {
        topicPrerequisiteRepository.save(TopicPrerequisite.link(topics.get(topic), topics.get(prerequisite)));
    }

    private void saveConcept(Map<String, Topic> topics, Map<String, Concept> concepts, String topic, String name) {
        Concept concept = new Concept();
        concept.setTopic(topics.get(topic));
        concept.setName(name);
        concepts.put(topic + "/" + name, conceptRepository.save(concept));
    }

    private static Concept concept(Map<String, Concept> concepts, String topic, String name) {
        Concept concept = concepts.get(topic + "/" + name);
        if (concept == null) {
            throw new IllegalStateException("Unknown seed concept: " + topic + "/" + name);
        }
        return concept;
    }

    private void saveQuestion(Topic topic, Concept concept, String title, String slug, Difficulty difficulty,
            String placementTags, String description, String starterCode, String constraints, int timeLimitMs,
            int memoryLimitKb, List<Case> cases) {
        Question question = new Question();
        question.setTopic(topic);
        question.setConcept(concept);
        question.setTitle(title);
        question.setSlug(slug);
        question.setDescription(description);
        question.setDifficulty(difficulty);
        question.setPlacementTags(placementTags);
        question.setStarterCodeJava(starterCode);
        question.setConstraints(constraints);
        question.setTimeLimitMs(timeLimitMs);
        question.setMemoryLimitKb(memoryLimitKb);
        Question saved = questionRepository.save(question);

        List<TestCase> rows = cases.stream().map(c -> {
            TestCase testCase = new TestCase();
            testCase.setQuestion(saved);
            testCase.setInputData(c.input());
            testCase.setExpectedOutput(c.expectedOutput());
            testCase.setHidden(c.hidden());
            return testCase;
        }).toList();
        testCaseRepository.saveAll(rows);
    }

    private void saveDiagnostic(Topic topic, Concept concept, String title, String slug, Difficulty difficulty,
            String description, String correctOption) {
        saveQuestion(topic, concept, title, slug, difficulty, DIAGNOSTIC_TAG, description, null, null,
                DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
                List.of(new Case(DIAGNOSTIC_INPUT, correctOption, true)));
    }

    private void seedArrays(Map<String, Topic> topics, Map<String, Concept> concepts) {
        saveQuestion(topics.get("Arrays"), concept(concepts, "Arrays", "Two Pointers"),
            "Two Sum in a Sorted Array", "two-sum-sorted", Difficulty.EASY, "Amazon,Microsoft,TCS",
            """
                Given an array of `n` integers sorted in **non-decreasing** order and an integer `target`, find the two different positions `i < j` (1-based) such that `nums[i] + nums[j] == target`.

                Exactly one such pair exists in every test case. Aim for **O(n) time and O(1) extra space**.

                ## Input Format
                - Line 1: two integers `n` and `target`.
                - Line 2: `n` space-separated integers in non-decreasing order.

                ## Output Format
                Two integers `i j` separated by a single space.

                ## Example

                **Input**

                ```
                4 9
                2 7 11 15
                ```

                **Output**

                ```
                1 2
                ```

                `2 + 7 = 9`, which are the 1st and 2nd elements.
                """,
            """
                import java.util.*;

                public class Main {

                    static int[] findPair(int[] nums, int target) {
                        // Write your solution here
                        return new int[]{0, 0};
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int target = sc.nextInt();
                        int[] nums = new int[n];
                        for (int i = 0; i < n; i++) nums[i] = sc.nextInt();
                        int[] pair = findPair(nums, target);
                        System.out.println(pair[0] + " " + pair[1]);
                    }
                }
                """,
            "2 <= n <= 100000\n-10^9 <= nums[i], target <= 10^9\nnums is sorted in non-decreasing order\nExactly one valid pair exists", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("4 9\n2 7 11 15\n", "1 2", false),
                new Case("3 6\n2 3 4\n", "1 3", false),
                new Case("2 -1\n-5 4\n", "1 2", true),
                new Case("5 8\n1 4 4 6 9\n", "2 3", true),
                new Case("7 60\n3 8 17 22 38 45 51\n", "4 5", true)));
        saveQuestion(topics.get("Arrays"), concept(concepts, "Arrays", "Prefix Sum"),
            "Range Sum Queries", "range-sum-queries", Difficulty.EASY, "Infosys,Wipro,Amazon",
            """
                You are given an array of `n` integers and `q` queries. Each query gives two 1-based indices `l` and `r`; answer it with the sum of `nums[l..r]` (both ends inclusive).

                Answer every query in **O(1)** after an O(n) preprocessing step. Sums can exceed the 32-bit integer range.

                ## Input Format
                - Line 1: two integers `n` and `q`.
                - Line 2: `n` space-separated integers.
                - Next `q` lines: two integers `l` and `r`.

                ## Output Format
                `q` lines, the answer to each query in order.

                ## Example

                **Input**

                ```
                5 3
                1 2 3 4 5
                1 5
                2 4
                3 3
                ```

                **Output**

                ```
                15
                9
                3
                ```

                The queries ask for `1+2+3+4+5 = 15`, `2+3+4 = 9` and `3`.
                """,
            """
                import java.util.*;

                public class Main {

                    static long[] rangeSums(int[] nums, int[][] queries) {
                        // Write your solution here
                        return new long[queries.length];
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int q = sc.nextInt();
                        int[] nums = new int[n];
                        for (int i = 0; i < n; i++) nums[i] = sc.nextInt();
                        int[][] queries = new int[q][2];
                        for (int i = 0; i < q; i++) {
                            queries[i][0] = sc.nextInt();
                            queries[i][1] = sc.nextInt();
                        }
                        long[] result = rangeSums(nums, queries);
                        StringBuilder sb = new StringBuilder();
                        for (long value : result) sb.append(value).append('\\n');
                        System.out.print(sb);
                    }
                }
                """,
            "1 <= n, q <= 100000\n-10^9 <= nums[i] <= 10^9\n1 <= l <= r <= n", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("5 3\n1 2 3 4 5\n1 5\n2 4\n3 3\n", "15\n9\n3", false),
                new Case("4 2\n-1 5 -3 2\n1 2\n3 4\n", "4\n-1", false),
                new Case("3 2\n1000000000 1000000000 1000000000\n1 3\n2 3\n", "3000000000\n2000000000", true),
                new Case("1 1\n7\n1 1\n", "7", true),
                new Case("8 4\n3 -2 7 0 -5 4 1 6\n1 8\n4 6\n2 2\n5 8\n", "14\n-1\n-2\n6", true)));
        saveQuestion(topics.get("Arrays"), concept(concepts, "Arrays", "Sliding Window"),
            "Maximum Sum of a Subarray of Size K", "max-sum-subarray-k", Difficulty.EASY, "Amazon,Flipkart,Cognizant",
            """
                Given an array of `n` integers and a window size `k`, find the **maximum sum** over all contiguous subarrays of length exactly `k`.

                Re-use the previous window's sum instead of recomputing it, so the solution runs in **O(n)**. Sums can exceed the 32-bit integer range.

                ## Input Format
                - Line 1: two integers `n` and `k`.
                - Line 2: `n` space-separated integers.

                ## Output Format
                A single integer: the maximum window sum.

                ## Example

                **Input**

                ```
                6 3
                1 4 2 10 2 3
                ```

                **Output**

                ```
                16
                ```

                The best window of size 3 is `[4, 2, 10]` with sum 16.
                """,
            """
                import java.util.*;

                public class Main {

                    static long maxSumOfSize(int[] nums, int k) {
                        // Write your solution here
                        return 0;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int k = sc.nextInt();
                        int[] nums = new int[n];
                        for (int i = 0; i < n; i++) nums[i] = sc.nextInt();
                        System.out.println(maxSumOfSize(nums, k));
                    }
                }
                """,
            "1 <= k <= n <= 100000\n-2 * 10^9 <= nums[i] <= 2 * 10^9", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("6 3\n1 4 2 10 2 3\n", "16", false),
                new Case("5 2\n-3 -1 -4 -1 -5\n", "-4", false),
                new Case("1 1\n42\n", "42", true),
                new Case("4 4\n5 -2 7 1\n", "11", true),
                new Case("4 2\n2000000000 2000000000 -5 1\n", "4000000000", true)));
    }

    private void seedStrings(Map<String, Topic> topics, Map<String, Concept> concepts) {
        saveQuestion(topics.get("Strings"), concept(concepts, "Strings", "Frequency Counting"),
            "Valid Anagram", "valid-anagram", Difficulty.EASY, "TCS,Accenture,Amazon",
            """
                Two strings are **anagrams** if one can be rearranged into the other using every character exactly once. Given two strings `s` and `t` of lowercase English letters, decide whether they are anagrams.

                Use a count array of size 26 to solve it in **O(n)** time.

                ## Input Format
                - Line 1: the string `s`.
                - Line 2: the string `t`.

                ## Output Format
                Print `true` if `t` is an anagram of `s`, otherwise `false`.

                ## Example

                **Input**

                ```
                anagram
                nagaram
                ```

                **Output**

                ```
                true
                ```

                Rearranging `anagram` gives `nagaram`.
                """,
            """
                import java.util.*;

                public class Main {

                    static boolean isAnagram(String s, String t) {
                        // Write your solution here
                        return false;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        String s = sc.next();
                        String t = sc.next();
                        System.out.println(isAnagram(s, t));
                    }
                }
                """,
            "1 <= s.length, t.length <= 100000\nBoth strings contain only lowercase letters 'a' to 'z'", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("anagram\nnagaram\n", "true", false),
                new Case("rat\ncar\n", "false", false),
                new Case("a\na\n", "true", true),
                new Case("ab\na\n", "false", true),
                new Case("aacc\nccac\n", "false", true),
                new Case("listen\nsilent\n", "true", true)));
        saveQuestion(topics.get("Strings"), concept(concepts, "Strings", "Frequency Counting"),
            "First Unique Character", "first-unique-character", Difficulty.EASY, "Microsoft,Infosys,Zoho",
            """
                Given a string `s` of lowercase letters, find the **first character that appears exactly once** and print its 0-based index. If every character repeats, print `-1`.

                ## Input Format
                A single line containing the string `s`.

                ## Output Format
                A single integer: the index of the first non-repeating character, or `-1`.

                ## Example

                **Input**

                ```
                leetcode
                ```

                **Output**

                ```
                0
                ```

                `l` is the first letter that never repeats and sits at index 0.
                """,
            """
                import java.util.*;

                public class Main {

                    static int firstUnique(String s) {
                        // Write your solution here
                        return -1;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        String s = sc.next();
                        System.out.println(firstUnique(s));
                    }
                }
                """,
            "1 <= s.length <= 100000\ns contains only lowercase letters 'a' to 'z'", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("leetcode\n", "0", false),
                new Case("loveleetcode\n", "2", false),
                new Case("aabb\n", "-1", true),
                new Case("z\n", "0", true),
                new Case("aabbcdd\n", "4", true)));
        saveQuestion(topics.get("Strings"), concept(concepts, "Strings", "Palindromes"),
            "Longest Palindromic Substring Length", "longest-palindromic-substring-length", Difficulty.MEDIUM, "Amazon,Google,Adobe",
            """
                A **palindrome** reads the same forwards and backwards. Given a string `s`, print the **length** of the longest palindromic substring (a contiguous run of characters).

                Expanding around each of the `2n - 1` possible centres solves it in **O(n^2)** time and O(1) space.

                ## Input Format
                A single line containing the string `s`.

                ## Output Format
                A single integer: the length of the longest palindromic substring.

                ## Example

                **Input**

                ```
                babad
                ```

                **Output**

                ```
                3
                ```

                `bab` (or `aba`) is a longest palindromic substring.
                """,
            """
                import java.util.*;

                public class Main {

                    static int longestPalindromeLength(String s) {
                        // Write your solution here
                        return 0;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        String s = sc.next();
                        System.out.println(longestPalindromeLength(s));
                    }
                }
                """,
            "1 <= s.length <= 1000\ns contains only lowercase letters 'a' to 'z'", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("babad\n", "3", false),
                new Case("cbbd\n", "2", false),
                new Case("a\n", "1", true),
                new Case("abcde\n", "1", true),
                new Case("forgeeksskeegfor\n", "10", true),
                new Case("aaaa\n", "4", true)));
    }

    private void seedBinarySearch(Map<String, Topic> topics, Map<String, Concept> concepts) {
        saveQuestion(topics.get("Binary Search"), concept(concepts, "Binary Search", "Search on Sorted Array"),
            "Binary Search", "binary-search", Difficulty.EASY, "TCS,Wipro,Amazon",
            """
                Given a sorted array of `n` **distinct** integers in ascending order and a `target`, print the 0-based index of `target`, or `-1` if it is not present.

                The solution must run in **O(log n)** time.

                ## Input Format
                - Line 1: two integers `n` and `target`.
                - Line 2: `n` space-separated integers in strictly ascending order.

                ## Output Format
                A single integer: the index of `target`, or `-1`.

                ## Example

                **Input**

                ```
                6 9
                -1 0 3 5 9 12
                ```

                **Output**

                ```
                4
                ```

                `9` is stored at index 4.
                """,
            """
                import java.util.*;

                public class Main {

                    static int search(int[] nums, int target) {
                        // Write your solution here
                        return -1;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int target = sc.nextInt();
                        int[] nums = new int[n];
                        for (int i = 0; i < n; i++) nums[i] = sc.nextInt();
                        System.out.println(search(nums, target));
                    }
                }
                """,
            "1 <= n <= 100000\n-10^9 <= nums[i], target <= 10^9\nAll values in nums are distinct and sorted ascending", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("6 9\n-1 0 3 5 9 12\n", "4", false),
                new Case("6 2\n-1 0 3 5 9 12\n", "-1", false),
                new Case("1 5\n5\n", "0", true),
                new Case("1 3\n5\n", "-1", true),
                new Case("7 -10\n-10 -4 0 2 8 15 99\n", "0", true),
                new Case("7 99\n-10 -4 0 2 8 15 99\n", "6", true)));
        saveQuestion(topics.get("Binary Search"), concept(concepts, "Binary Search", "Search on Sorted Array"),
            "First Occurrence in a Sorted Array", "first-occurrence-sorted-array", Difficulty.MEDIUM, "Amazon,Microsoft,Paytm",
            """
                Given an array of `n` integers sorted in **non-decreasing** order (duplicates allowed) and a `target`, print the **smallest index** at which `target` appears, or `-1` if it does not appear.

                Keep searching the left half after a match; the solution must run in **O(log n)** time.

                ## Input Format
                - Line 1: two integers `n` and `target`.
                - Line 2: `n` space-separated integers in non-decreasing order.

                ## Output Format
                A single integer: the first index of `target`, or `-1`.

                ## Example

                **Input**

                ```
                8 3
                1 2 3 3 3 3 4 5
                ```

                **Output**

                ```
                2
                ```

                `3` occurs at indices 2 to 5; the first one is index 2.
                """,
            """
                import java.util.*;

                public class Main {

                    static int firstOccurrence(int[] nums, int target) {
                        // Write your solution here
                        return -1;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int target = sc.nextInt();
                        int[] nums = new int[n];
                        for (int i = 0; i < n; i++) nums[i] = sc.nextInt();
                        System.out.println(firstOccurrence(nums, target));
                    }
                }
                """,
            "1 <= n <= 100000\n-10^9 <= nums[i], target <= 10^9\nnums is sorted in non-decreasing order", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("8 3\n1 2 3 3 3 3 4 5\n", "2", false),
                new Case("5 6\n1 2 3 4 5\n", "-1", false),
                new Case("6 7\n7 7 7 7 7 7\n", "0", true),
                new Case("5 1\n1 1 2 2 3\n", "0", true),
                new Case("7 5\n1 2 2 4 5 5 5\n", "4", true)));
        saveQuestion(topics.get("Binary Search"), concept(concepts, "Binary Search", "Search on Answer"),
            "Minimum Eating Speed", "minimum-eating-speed", Difficulty.MEDIUM, "Amazon,Google,Swiggy",
            """
                There are `n` piles of bananas; pile `i` has `piles[i]` bananas. A guard returns in `h` hours. Each hour you choose one pile and eat up to `k` bananas from it (if the pile has fewer than `k`, you finish it and eat nothing more that hour).

                Find the **minimum integer speed `k`** that lets you eat every banana within `h` hours. Binary search on `k`: the check "can I finish at speed `k`?" is monotonic.

                ## Input Format
                - Line 1: two integers `n` and `h`.
                - Line 2: `n` space-separated integers, the pile sizes.

                ## Output Format
                A single integer: the minimum speed `k`.

                ## Example

                **Input**

                ```
                4 8
                3 6 7 11
                ```

                **Output**

                ```
                4
                ```

                At speed 4 the piles take 1 + 2 + 2 + 3 = 8 hours; speed 3 would need 10.
                """,
            """
                import java.util.*;

                public class Main {

                    static int minEatingSpeed(int[] piles, int h) {
                        // Write your solution here
                        return 0;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int h = sc.nextInt();
                        int[] piles = new int[n];
                        for (int i = 0; i < n; i++) piles[i] = sc.nextInt();
                        System.out.println(minEatingSpeed(piles, h));
                    }
                }
                """,
            "1 <= n <= h <= 10^9\n1 <= piles[i] <= 10^9", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("4 8\n3 6 7 11\n", "4", false),
                new Case("5 6\n30 11 23 4 20\n", "23", false),
                new Case("1 1\n1000000000\n", "1000000000", true),
                new Case("3 3\n1 1 1\n", "1", true),
                new Case("4 4\n10 20 30 40\n", "40", true),
                new Case("3 7\n1000000000 999999999 5\n", "333333334", true)));
    }

    private void seedStacks(Map<String, Topic> topics, Map<String, Concept> concepts) {
        saveQuestion(topics.get("Stacks"), concept(concepts, "Stacks", "Stack Applications"),
            "Valid Parentheses", "valid-parentheses", Difficulty.EASY, "Amazon,Microsoft,Infosys",
            """
                Given a string made only of the characters `(`, `)`, `[`, `]`, `{` and `}`, decide whether it is **valid**: every opening bracket must be closed by the same type of bracket, and brackets must close in the correct order.

                ## Input Format
                A single line containing the bracket string `s`.

                ## Output Format
                Print `true` if `s` is valid, otherwise `false`.

                ## Example

                **Input**

                ```
                ()[]{}
                ```

                **Output**

                ```
                true
                ```

                Every opening bracket is closed by its own type in the right order.
                """,
            """
                import java.util.*;

                public class Main {

                    static boolean isValid(String s) {
                        // Write your solution here
                        return false;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        String s = sc.next();
                        System.out.println(isValid(s));
                    }
                }
                """,
            "1 <= s.length <= 100000\ns consists only of the characters ()[]{}", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("()[]{}\n", "true", false),
                new Case("(]\n", "false", false),
                new Case("{[]}\n", "true", true),
                new Case("([)]\n", "false", true),
                new Case("(((\n", "false", true),
                new Case(")(\n", "false", true)));
        saveQuestion(topics.get("Stacks"), concept(concepts, "Stacks", "Stack Applications"),
            "Next Greater Element", "next-greater-element", Difficulty.MEDIUM, "Amazon,Adobe,Capgemini",
            """
                For every element of an array, find the **first element to its right that is strictly greater**. If there is none, the answer for that position is `-1`.

                A monotonic stack solves it in **O(n)** time.

                ## Input Format
                - Line 1: the integer `n`.
                - Line 2: `n` space-separated integers.

                ## Output Format
                `n` space-separated integers: the next greater element for each position.

                ## Example

                **Input**

                ```
                4
                4 5 2 25
                ```

                **Output**

                ```
                5 25 25 -1
                ```

                For `4` the next greater to the right is `5`; for `25` there is none, so `-1`.
                """,
            """
                import java.util.*;

                public class Main {

                    static int[] nextGreater(int[] nums) {
                        // Write your solution here
                        int[] answer = new int[nums.length];
                        Arrays.fill(answer, -1);
                        return answer;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int[] nums = new int[n];
                        for (int i = 0; i < n; i++) nums[i] = sc.nextInt();
                        int[] answer = nextGreater(nums);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < answer.length; i++) {
                            if (i > 0) sb.append(' ');
                            sb.append(answer[i]);
                        }
                        System.out.println(sb);
                    }
                }
                """,
            "1 <= n <= 100000\n-10^9 <= nums[i] <= 10^9", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("4\n4 5 2 25\n", "5 25 25 -1", false),
                new Case("3\n3 2 1\n", "-1 -1 -1", false),
                new Case("1\n7\n", "-1", true),
                new Case("6\n1 3 2 4 2 5\n", "3 4 4 5 5 -1", true),
                new Case("5\n5 5 5 5 5\n", "-1 -1 -1 -1 -1", true)));
        saveQuestion(topics.get("Stacks"), concept(concepts, "Stacks", "Stack Applications"),
            "Largest Rectangle in a Histogram", "largest-rectangle-histogram", Difficulty.HARD, "Amazon,Google,Microsoft",
            """
                A histogram has `n` bars of width 1 and the given heights. Find the **area of the largest rectangle** that fits entirely inside the histogram.

                A brute force over all ranges is O(n^2); a monotonic stack of increasing heights gives **O(n)**. The area can exceed the 32-bit integer range, so use `long`.

                ## Input Format
                - Line 1: the integer `n`.
                - Line 2: `n` space-separated bar heights.

                ## Output Format
                A single integer: the largest rectangle area.

                ## Example

                **Input**

                ```
                6
                2 1 5 6 2 3
                ```

                **Output**

                ```
                10
                ```

                The bars of height 5 and 6 form a rectangle of width 2 and height 5, area 10.
                """,
            """
                import java.util.*;

                public class Main {

                    static long largestRectangle(int[] heights) {
                        // Write your solution here
                        return 0;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int[] heights = new int[n];
                        for (int i = 0; i < n; i++) heights[i] = sc.nextInt();
                        System.out.println(largestRectangle(heights));
                    }
                }
                """,
            "1 <= n <= 100000\n0 <= heights[i] <= 10^9", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("6\n2 1 5 6 2 3\n", "10", false),
                new Case("2\n2 4\n", "4", false),
                new Case("1\n5\n", "5", true),
                new Case("5\n6 6 6 6 6\n", "30", true),
                new Case("7\n1 2 3 4 5 6 7\n", "16", true),
                new Case("6\n4 2 0 3 2 5\n", "6", true)));
    }

    private void seedDynamicProgramming(Map<String, Topic> topics, Map<String, Concept> concepts) {
        saveQuestion(topics.get("Dynamic Programming"), concept(concepts, "Dynamic Programming", "1D Dynamic Programming"),
            "House Robber", "house-robber", Difficulty.MEDIUM, "Amazon,Google,Cognizant",
            """
                Houses stand in a row and house `i` holds `nums[i]` money. You cannot rob **two adjacent houses**. Find the maximum total you can rob.

                At each house decide: rob it (add its value to the best total up to two houses back) or skip it (keep the best total so far).

                ## Input Format
                - Line 1: the integer `n`.
                - Line 2: `n` space-separated non-negative integers.

                ## Output Format
                A single integer: the maximum amount you can rob.

                ## Example

                **Input**

                ```
                4
                1 2 3 1
                ```

                **Output**

                ```
                4
                ```

                Robbing houses 1 and 3 gives `1 + 3 = 4`.
                """,
            """
                import java.util.*;

                public class Main {

                    static long rob(int[] nums) {
                        // Write your solution here
                        return 0;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int[] nums = new int[n];
                        for (int i = 0; i < n; i++) nums[i] = sc.nextInt();
                        System.out.println(rob(nums));
                    }
                }
                """,
            "1 <= n <= 100000\n0 <= nums[i] <= 10^9", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("4\n1 2 3 1\n", "4", false),
                new Case("5\n2 7 9 3 1\n", "12", false),
                new Case("1\n5\n", "5", true),
                new Case("2\n3 9\n", "9", true),
                new Case("6\n2 1 1 2 10 1\n", "13", true),
                new Case("4\n1000000000 1 1 1000000000\n", "2000000000", true)));
        saveQuestion(topics.get("Dynamic Programming"), concept(concepts, "Dynamic Programming", "1D Dynamic Programming"),
            "Minimum Coin Change", "minimum-coin-change", Difficulty.MEDIUM, "Amazon,Microsoft,Goldman Sachs",
            """
                You have `n` coin denominations and an unlimited supply of each. Find the **fewest coins** needed to make exactly `amount`. If it is impossible, print `-1`. Making an amount of `0` needs `0` coins.

                Greedy choices can fail here (for coins `1 3 4` and amount `6`), so build a table `dp[a]` = fewest coins for amount `a`.

                ## Input Format
                - Line 1: two integers `n` and `amount`.
                - Line 2: `n` space-separated coin values.

                ## Output Format
                A single integer: the minimum number of coins, or `-1`.

                ## Example

                **Input**

                ```
                3 11
                1 2 5
                ```

                **Output**

                ```
                3
                ```

                `11 = 5 + 5 + 1` uses three coins.
                """,
            """
                import java.util.*;

                public class Main {

                    static int minCoins(int[] coins, int amount) {
                        // Write your solution here
                        return -1;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        int amount = sc.nextInt();
                        int[] coins = new int[n];
                        for (int i = 0; i < n; i++) coins[i] = sc.nextInt();
                        System.out.println(minCoins(coins, amount));
                    }
                }
                """,
            "1 <= n <= 12\n1 <= coins[i] <= 10000\n0 <= amount <= 10000", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("3 11\n1 2 5\n", "3", false),
                new Case("1 3\n2\n", "-1", false),
                new Case("1 0\n1\n", "0", true),
                new Case("3 6\n1 3 4\n", "2", true),
                new Case("2 7\n2 4\n", "-1", true),
                new Case("4 27\n2 5 10 25\n", "2", true)));
        saveQuestion(topics.get("Dynamic Programming"), concept(concepts, "Dynamic Programming", "2D Dynamic Programming"),
            "Edit Distance", "edit-distance", Difficulty.HARD, "Amazon,Google,Microsoft",
            """
                Given two words `a` and `b`, find the **minimum number of operations** to convert `a` into `b`. In one operation you may **insert** a character, **delete** a character, or **replace** a character.

                Let `dp[i][j]` be the edit distance between the first `i` characters of `a` and the first `j` characters of `b`.

                ## Input Format
                - Line 1: the string `a`.
                - Line 2: the string `b`.

                ## Output Format
                A single integer: the minimum number of operations.

                ## Example

                **Input**

                ```
                horse
                ros
                ```

                **Output**

                ```
                3
                ```

                `horse -> rorse` (replace), `rorse -> rose` (delete), `rose -> ros` (delete).
                """,
            """
                import java.util.*;

                public class Main {

                    static int editDistance(String a, String b) {
                        // Write your solution here
                        return 0;
                    }

                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        String a = sc.next();
                        String b = sc.next();
                        System.out.println(editDistance(a, b));
                    }
                }
                """,
            "1 <= a.length, b.length <= 1000\nBoth strings contain only lowercase letters 'a' to 'z'", DEFAULT_TIME_LIMIT_MS, DEFAULT_MEMORY_LIMIT_KB,
            List.of(
                new Case("horse\nros\n", "3", false),
                new Case("intention\nexecution\n", "5", false),
                new Case("a\na\n", "0", true),
                new Case("abc\nxyz\n", "3", true),
                new Case("sunday\nsaturday\n", "3", true),
                new Case("kitten\nsitting\n", "3", true)));
    }

    private void seedDiagnosticQuestions(Map<String, Topic> topics, Map<String, Concept> concepts) {
        saveDiagnostic(topics.get("Arrays"), concept(concepts, "Arrays", "Two Pointers"),
            "Diagnostic: Pair sum in a sorted array", "diag-arrays-two-pointers", Difficulty.EASY,
            """
                **Diagnostic question (multiple choice)**

                Which technique finds two numbers that add up to a target in a **sorted** array in O(n) time using only O(1) extra space?

                **Options**

                A. Check every pair with two nested loops
                B. Store seen values in a hash set
                C. Move one pointer from each end towards the middle
                D. Sort the array again before searching

                Answer with a single letter: A, B, C or D.
                """,
            "C");
        saveDiagnostic(topics.get("Arrays"), concept(concepts, "Arrays", "Prefix Sum"),
            "Diagnostic: Prefix sum output", "diag-arrays-prefix-sum", Difficulty.EASY,
            """
                **Diagnostic question (multiple choice)**

                What does this code print?

                ```java
                int[] a = {2, 4, 6, 8};
                int[] p = new int[5];
                for (int i = 0; i < 4; i++) p[i + 1] = p[i] + a[i];
                System.out.println(p[3] - p[1]);
                ```

                **Options**

                A. 6
                B. 10
                C. 12
                D. 14

                Answer with a single letter: A, B, C or D.
                """,
            "B");
        saveDiagnostic(topics.get("Strings"), concept(concepts, "Strings", "Frequency Counting"),
            "Diagnostic: Anagram check complexity", "diag-strings-frequency", Difficulty.EASY,
            """
                **Diagnostic question (multiple choice)**

                Two strings of length `n` contain only lowercase letters. What is the time complexity of checking whether they are anagrams using a 26-element count array?

                **Options**

                A. O(n^2)
                B. O(n log n)
                C. O(n)
                D. O(26^n)

                Answer with a single letter: A, B, C or D.
                """,
            "C");
        saveDiagnostic(topics.get("Strings"), concept(concepts, "Strings", "Palindromes"),
            "Diagnostic: Palindrome pointer trace", "diag-strings-palindrome", Difficulty.MEDIUM,
            """
                **Diagnostic question (multiple choice)**

                What does this code print?

                ```java
                String s = "abcba";
                int l = 0, r = s.length() - 1, steps = 0;
                while (l < r && s.charAt(l) == s.charAt(r)) { l++; r--; steps++; }
                System.out.println(steps + " " + (l >= r));
                ```

                **Options**

                A. 1 false
                B. 2 false
                C. 3 true
                D. 2 true

                Answer with a single letter: A, B, C or D.
                """,
            "D");
        saveDiagnostic(topics.get("Binary Search"), concept(concepts, "Binary Search", "Search on Answer"),
            "Diagnostic: When can you binary search on the answer?", "diag-binary-search-monotonic", Difficulty.MEDIUM,
            """
                **Diagnostic question (multiple choice)**

                Which condition must hold for binary search on the **answer** to give a correct result?

                **Options**

                A. The feasibility check is monotonic over the answer range (once true, it stays true as the value grows)
                B. The input array is already sorted
                C. The answer is always a prime number
                D. The search range has a power-of-two size

                Answer with a single letter: A, B, C or D.
                """,
            "A");
        saveDiagnostic(topics.get("Binary Search"), concept(concepts, "Binary Search", "Search on Sorted Array"),
            "Diagnostic: Binary search trace", "diag-binary-search-lower-bound", Difficulty.MEDIUM,
            """
                **Diagnostic question (multiple choice)**

                What does this code print?

                ```java
                int[] a = {1, 3, 5, 7, 9, 11};
                int lo = 0, hi = a.length - 1, ans = -1;
                while (lo <= hi) {
                    int mid = (lo + hi) / 2;
                    if (a[mid] >= 6) { ans = mid; hi = mid - 1; } else { lo = mid + 1; }
                }
                System.out.println(ans);
                ```

                **Options**

                A. 2
                B. 3
                C. 4
                D. -1

                Answer with a single letter: A, B, C or D.
                """,
            "B");
        saveDiagnostic(topics.get("Stacks"), concept(concepts, "Stacks", "Stack Applications"),
            "Diagnostic: Balanced brackets", "diag-stacks-brackets", Difficulty.EASY,
            """
                **Diagnostic question (multiple choice)**

                Which data structure is the natural choice for checking whether the brackets in an expression are balanced?

                **Options**

                A. Queue
                B. Min-heap
                C. Hash map
                D. Stack

                Answer with a single letter: A, B, C or D.
                """,
            "D");
        saveDiagnostic(topics.get("Stacks"), concept(concepts, "Stacks", "Stack Applications"),
            "Diagnostic: Monotonic stack trace", "diag-stacks-monotonic", Difficulty.MEDIUM,
            """
                **Diagnostic question (multiple choice)**

                What does this code print?

                ```java
                Deque<Integer> st = new ArrayDeque<>();
                int[] a = {4, 2, 7, 1};
                int count = 0;
                for (int x : a) {
                    while (!st.isEmpty() && st.peek() < x) { st.pop(); count++; }
                    st.push(x);
                }
                System.out.println(count + " " + st.size());
                ```

                **Options**

                A. 2 2
                B. 1 2
                C. 2 3
                D. 3 2

                Answer with a single letter: A, B, C or D.
                """,
            "A");
        saveDiagnostic(topics.get("Dynamic Programming"), concept(concepts, "Dynamic Programming", "1D Dynamic Programming"),
            "Diagnostic: When does DP apply?", "diag-dp-properties", Difficulty.EASY,
            """
                **Diagnostic question (multiple choice)**

                Which two properties should a problem have for dynamic programming to be applicable?

                **Options**

                A. A greedy choice property and sorted input
                B. Independent halves and random access
                C. Overlapping subproblems and optimal substructure
                D. Negative edge weights and cycles

                Answer with a single letter: A, B, C or D.
                """,
            "C");
        saveDiagnostic(topics.get("Dynamic Programming"), concept(concepts, "Dynamic Programming", "1D Dynamic Programming"),
            "Diagnostic: DP table output", "diag-dp-table", Difficulty.MEDIUM,
            """
                **Diagnostic question (multiple choice)**

                What does this code print?

                ```java
                int[] dp = new int[6];
                dp[0] = 1;
                for (int i = 1; i <= 5; i++) {
                    dp[i] = dp[i - 1];
                    if (i >= 2) dp[i] += dp[i - 2];
                }
                System.out.println(dp[5]);
                ```

                **Options**

                A. 5
                B. 13
                C. 10
                D. 8

                Answer with a single letter: A, B, C or D.
                """,
            "D");
    }
}
