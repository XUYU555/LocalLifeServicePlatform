package com.hmdp;

import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author xy
 * @date 2024-05-23 17:14
 */
public class Test1 {

    public static Set<String> pers = new HashSet<>();

    public static void main(String[] args) {
        String s = "aaa";
        System.out.println(s.length());
        String[] words = new String[]{"a", "b"};
        List<Integer> result = new ArrayList<>();
        permutation(words, 0);
        int l=0,r=words.length * words[0].length();
        while(r <= s.length()) {
            String substring = s.substring(l, r);
            System.out.println(substring);
            if(pers.contains(substring)) {
                result.add(l);
            }
            System.out.println("l:  " + l + "   r:  " + r);
            l++;
            r++;

        }
        result.forEach(integer -> System.out.println(integer));
        Integer v = 0;
        String s1 = String.valueOf(v);
        boolean b = StringUtils.hasLength(s1);
        System.out.println(s1);
        System.out.println(b);
    }

    // 排列
    public static void permutation(String[] data, int start) {
        if (start == data.length - 1) {
            String cur = "";
            for (int i = 0; i < data.length; i++) {
                cur += data[i];
            }
            pers.add(cur);
            System.out.println(cur);
        } else {
            for (int i = start; i < data.length; i++) {
                swap(data, start, i);
                permutation(data, start + 1);
                swap(data, start, i);
            }
        }
    }

    // 交换数组中的元素
    public static void swap(String[] data, int i, int j) {
        String temp = data[i];
        data[i] = data[j];
        data[j] = temp;
    }

}
