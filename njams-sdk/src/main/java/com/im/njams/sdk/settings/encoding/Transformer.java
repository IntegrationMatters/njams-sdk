/*
 * Copyright (c) 2018 Faiz & Siegeln Software GmbH
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * The Software shall be used for Good, not Evil.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk.settings.encoding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * This class encodes and decodes Strings
 * 
 * @author krautenberg@integrationmatters.com
 * @version 4.0.1
 */
public class Transformer {

    /**
     * A table of random numbers to be used as offset for password encryption.
     */
    private static final ArrayList<List<Integer>> PASSWORD_TRANSFORMER_MAP = new ArrayList<>();

    private static final int TRANSFORMATION_ROW_MAX_DIGITS;

    /**
     * A sequence of spaces to put in front of secret passwords to decide the
     * true length of them.
     */
    private static final String ANTI_TRIMMER = "               ";

    /**
     * A marker to identify encrypted passwords.
     */
    public static final String ENCRYPTION_MARKER = "??";

    static {
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(67, 73, 51, 84, 11, 36, -26, -31, 11, 15, 61, 64, -13, -2, -22, -24,
                56, 42, -8, -13, 42, -25, -16, 40, 65, 63, -10, -2, 75, -10, 51, 19, 24, 59, 17, 79, 9));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-31, -9, 60, 21, 28, -1, 33, -23, 57, 26, 82, 59, 28, 35, 49, -23,
                77, -30, 46, 27, -19, 45, -3, -22, 1, -7, 31, 85, -13, 66, 13, -14, 3, -17, 50, -9, 40));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(69, 8, -23, 77, 0, 79, 43, 33, 42, 69, -1, -12, -17, 42, 69, 7, 53,
                66, 18, 28, 63, -21, 46, 10, 12, 64, 77, 23, 73, 22, 86, 61, 43, -16, 81, 78, 78));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(61, 5, 22, 6, 73, 9, 79, 59, 85, 34, 69, 31, 81, 82, 1, 49, -6, -19,
                -12, -17, 78, 85, 4, 70, 67, 20, 24, -22, -3, -28, 83, 82, 10, -6, 60, 29, 72));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(0, 80, 25, -27, 81, -23, 77, -17, 14, -30, -18, 43, 81, -21, 71, 17,
                3, 1, 12, 80, 50, -29, 73, -25, 81, 44, -31, 53, 17, 58, -6, 52, 43, -27, 88, 9, 69));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(78, 1, 54, 19, -7, 13, 45, 69, 39, -13, 80, 31, -20, 39, -20, 3, 67,
                79, -31, 48, -18, -7, 35, 61, 19, -7, 78, 8, 17, 11, -13, -4, 74, 38, -4, 64, 65));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(67, 48, 47, -23, 6, 40, 35, 55, 40, 17, 22, 1, 75, 34, 67, -5, 77,
                -5, -4, 56, -31, 27, 0, -8, 19, -14, 21, 6, 84, 81, 24, -12, 2, -5, -19, 79, 70));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(87, 33, 83, 38, 29, 48, 22, 84, -9, -1, -17, 22, 66, 52, 11, -18, 33,
                42, 64, 81, 6, -11, 83, -19, 88, -20, 47, 79, 43, 10, 23, -1, 28, 45, 63, 2, -8));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(28, -12, 40, 21, 46, 19, 60, 73, 38, 30, 13, 20, -3, 6, 56, 17, 39,
                49, 73, -2, 67, 39, -13, 45, -14, 10, 85, -26, 33, 69, -25, 47, 17, 52, -19, 82, 30));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(79, 72, -19, 70, 50, 40, 5, 59, 17, -28, -25, 5, 40, -30, -26, 81,
                39, -12, 39, -11, 64, 29, 11, 15, 13, 69, -16, -16, 40, 87, 74, -21, 72, -15, 46, 68, -22));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(47, 13, 21, 14, 54, 60, 19, -28, 12, 87, -2, 63, 82, -12, 8, -26, 15,
                9, 80, -23, 83, 63, 55, 48, 33, 7, -23, 55, -26, 45, 38, 51, 44, 76, 50, 24, 49));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(53, 15, 47, 49, 56, 55, 18, 70, 31, 58, 59, 43, 17, 49, 54, 82, -23,
                6, 46, 3, 47, -30, 10, 55, -19, 26, 42, -15, 9, 80, 53, 83, 83, 45, -1, 33, 4));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-20, 7, 42, -12, 56, 48, 47, 11, 29, 38, -17, 27, -28, 70, 81, 53,
                47, -6, 64, 18, -23, 82, 35, 87, 43, 58, 79, 77, 11, 55, -15, -23, 63, -19, 42, 69, 1));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(15, 60, 48, 79, 83, 26, 3, 27, 8, 33, 23, 45, -31, 26, 39, -8, 63,
                21, 12, 22, 81, 43, 53, 25, 68, 42, 13, 3, 19, 34, 39, -27, -29, 42, 54, 32, 85));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(88, 21, 43, 63, 38, -5, 58, 2, -17, 4, 2, 32, -31, -9, 29, 64, 28,
                17, 46, 86, 12, 42, 3, 42, -23, -4, 44, 46, 74, 56, 60, 33, 12, 38, 6, -17, 75));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(73, 33, 54, 68, 74, 24, 29, -22, 23, 19, -29, -17, 75, -23, 50, 63,
                46, 27, 36, 87, 64, 59, -11, 0, -14, 36, 21, 61, 58, 17, 78, -29, 36, 53, 8, 10, -1));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(43, 78, 63, 70, 66, 48, 59, 65, -24, 6, 64, 44, 10, 87, 85, -10, 32,
                83, 81, 33, 33, 86, 13, 18, -27, 37, 1, 60, 35, 51, 37, -23, 52, 54, 17, 53, 71));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(78, 64, 45, 40, 23, 88, -26, 11, 63, -1, 7, -10, -2, -17, 49, 61,
                -18, 36, 54, 23, -11, -28, 57, 59, 81, -20, 9, -1, -19, -4, 31, 84, -8, 65, 60, 55, 10));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(14, 1, 60, -28, 44, -6, -25, 34, -15, 16, -30, -4, -7, 16, 5, 59, 43,
                88, -22, 88, 45, 60, -10, 71, 12, 32, 13, 53, 35, 68, 40, 23, 43, 72, 41, 87, 5));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(9, 77, 40, 44, 53, -20, -9, 13, -23, -27, 74, 45, 11, 70, 78, 35, 79,
                33, 61, 0, 57, -28, 58, 29, 34, 11, 38, -24, -8, 7, 38, 78, 20, 88, -12, 78, 58));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(26, 76, 62, 9, -7, 74, -30, 59, 9, 29, 88, -12, 32, 30, 17, 67, 22,
                -17, -31, 58, -9, -23, 67, 43, 74, 27, 17, 58, 7, 5, 52, 39, -17, 50, 27, 16, -19));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(81, 49, -9, 73, -29, 22, 24, 10, 0, -11, 64, 80, 41, -26, 53, 6, 35,
                22, 68, 71, 77, -18, 74, 76, 40, -18, 32, 25, -28, -9, 69, 11, 62, 38, 38, 61, -11));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(87, -4, 39, -5, 15, 11, -9, 55, 81, 64, 14, -19, 58, 76, 54, -18, 21,
                20, 33, 69, 8, 79, 7, 41, 51, 43, -10, -25, 61, 6, 69, -19, -15, 2, 27, -12, 29));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(9, -4, -31, 37, 68, 85, -27, 34, 62, 49, 85, 61, -15, 50, -10, 66,
                35, 84, -30, 33, 38, -5, -6, 9, 51, -9, 14, -13, -4, -7, 47, 72, 17, 24, 24, 29, 1));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(71, -14, 16, 65, 4, 64, -23, 83, 59, 63, 42, -28, -13, -7, 23, 75,
                53, 40, 61, 66, 5, 49, 40, 29, 25, 58, 58, 37, -18, -3, 37, 40, -28, 36, -13, 70, -16));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-24, -1, 45, 66, 58, 51, 62, 21, 76, 31, -20, -5, -28, -21, 53, 33,
                34, 12, 47, -21, 14, -19, 2, -1, -5, 31, -6, 74, -2, 8, -3, 12, 80, 70, 65, -12, 43));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(78, -29, 25, 37, 36, 61, -3, -28, 39, 39, 87, -11, 5, -11, 76, 49,
                -20, 37, -17, 4, 82, 55, 73, -30, 40, 7, 68, -17, 55, 61, 48, 42, 84, 8, 43, -8, -12));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(80, 79, 42, 10, 29, 67, 32, -20, -1, 41, 32, -30, 32, 7, 20, 77, -28,
                31, 75, 65, 44, 52, 81, 43, -3, 2, -6, 65, 33, 56, 57, 34, 68, 24, 21, -28, -10));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(72, 46, 6, 47, 79, 18, 9, 36, -5, 68, 32, -8, 39, 72, -4, 13, 60, 32,
                36, 36, 27, 5, -5, 78, 65, 38, 31, 71, 10, 39, 20, -29, 88, 60, 84, 58, -11));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(13, 14, -8, 81, 19, 36, -21, 22, 19, 68, 27, 7, 19, 17, 68, 76, 15,
                -31, -28, -24, -8, 5, 39, 43, 36, 0, 73, -6, 36, 27, 49, 71, 63, 80, -25, 29, 25));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(55, 21, 59, 59, -8, -5, 29, 48, -30, 68, 73, 22, -11, 1, -12, 20, -5,
                21, 85, 49, 67, -11, -4, 17, -29, -27, -8, 30, -27, -19, -27, 28, 54, -23, -17, 64, -6));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(69, 7, 14, -20, 20, 69, 65, 43, 33, -17, 61, -22, 48, 7, 44, -17, 64,
                70, 51, 39, -29, -30, 74, 41, 36, -25, 55, 2, 54, 76, -13, 34, 70, 43, 39, 44, 68));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-19, 44, 5, 81, 46, 2, 29, -15, 6, 46, 67, -10, 82, 3, 57, 7, 38, 68,
                -22, 69, 69, 50, 62, -21, 13, -24, -15, 19, 47, 81, 47, 75, 74, 15, 78, 64, -18));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(56, -23, -3, 43, 11, 60, 9, 0, 68, -6, 26, 36, 61, 3, -12, 63, -13,
                85, 64, -18, 21, 61, 54, 44, 24, -27, -4, 75, -8, 75, 54, 10, 76, -24, -7, -6, 59));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(74, 81, -21, 1, 50, 53, 51, 74, -7, -24, 68, -27, 8, 74, -14, -16,
                43, 85, -31, 48, 10, 37, 9, 34, 18, 8, 31, -29, -1, 9, 0, -31, -21, 58, -23, 16, 53));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(22, 30, 22, 19, 78, -3, 16, 43, 31, 9, -9, -13, 45, 69, 71, 19, -21,
                -13, 67, 38, 8, 47, 39, 68, 74, 50, -28, 23, 62, 6, 19, 76, -26, 74, 17, 18, 77));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-6, 42, -23, 68, 34, 13, 18, 49, 14, 23, 54, 26, -26, 9, 40, 23, 86,
                24, -23, 49, 39, -31, 50, 45, 57, -24, 73, 26, 7, 64, 34, 24, 40, -12, -19, 27, -7));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(5, 72, 9, 66, -30, 14, -27, -29, 12, 83, 88, 72, 59, -31, 20, 6, -10,
                -16, 19, 84, 4, 53, 79, 11, 35, 75, -16, 16, -16, 19, 20, -30, -5, -29, 75, -19, -19));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(65, 46, 28, 10, 66, 37, 52, -18, 53, 9, 25, 6, 73, 13, 48, 53, 26,
                19, 82, 82, 44, -5, 19, 61, -11, 38, -31, 85, -5, 76, 68, 87, -27, 20, -26, -25, 31));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(1, 27, 56, 43, 5, 30, 64, 46, -28, 7, 27, 16, 24, -28, 75, 35, -14,
                32, 48, -5, 64, 76, 53, 76, -9, -16, 51, 10, 1, -29, 86, 86, -21, 17, -4, 8, 31));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(64, -20, -21, 46, 50, 74, 82, 5, 9, 51, -17, 74, 18, 59, -26, 58, -4,
                52, 84, 3, -25, 65, -2, 70, 23, -22, 13, 15, -16, 52, 82, 33, 68, -12, 30, -26, 66));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(28, 64, -21, -6, -1, -1, 14, 64, 75, 49, -8, -20, 14, 0, 15, 44, 47,
                -14, 76, 15, 39, -28, 77, 12, -24, -18, 45, 80, 59, 50, 86, 40, 61, 66, 25, -16, 53));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(46, -8, 55, 36, 50, -1, -10, 78, -22, 34, -7, 56, 74, 15, 24, 81,
                -10, -9, 63, 77, 77, 16, 72, -4, -8, -31, -11, 41, 81, 30, 14, 82, -24, 50, 47, -28, 85));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-31, 39, 33, 6, 87, 88, -18, 87, 18, -16, 84, 83, 24, 30, 32, 56, 77,
                -31, -27, 69, 1, 52, 30, -24, 53, 46, 13, 22, 87, -30, 27, -21, 33, 83, 21, 54, 86));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(55, 82, 87, 76, -17, 82, 15, 17, -9, -22, 79, 57, 8, -17, -6, 26, 71,
                43, -7, 52, -30, 50, 73, 64, -24, 84, 40, 67, 61, 20, 69, -10, -7, -26, 75, 58, 26));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(88, -15, 24, 74, -14, -28, -31, 60, 3, 22, 80, 50, 39, -24, 55, 34,
                -26, 73, 44, 18, 22, 59, -14, 8, 73, 38, 34, 59, -23, 73, 75, 48, 46, 83, -25, 67, 83));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(78, 45, 59, 1, 55, 69, -8, -3, -22, -11, 40, 62, 30, 55, 71, 80, 41,
                61, 7, 74, 43, 65, 79, -6, 22, 39, 68, 12, 17, 17, 49, 25, -20, 50, 39, 77, 53));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(32, 24, -29, 23, 70, 45, -17, 24, 70, 64, 86, 69, -19, -28, 58, 85,
                40, 42, 84, 62, 1, -18, 5, 24, 34, 46, 36, 28, 23, 55, 66, -23, -15, 8, 56, -18, -18));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(83, 36, 42, 80, 47, -28, 44, 20, 76, -20, 1, 58, 2, -8, 4, 10, -16,
                70, 48, 88, 60, 61, 58, 85, -23, 44, 4, 80, 17, 79, 88, 72, 88, 52, 1, 69, 56));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(10, 68, 51, 14, 46, 29, 27, 38, 80, 19, 35, 42, 42, -29, 75, 42, 17,
                88, 40, 4, 77, 40, 28, 47, 19, -23, 8, -31, -6, 88, -29, 20, 76, -5, 68, 33, -2));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-16, 25, -15, 58, 58, 14, 10, 31, -21, 5, -22, 27, 73, 69, -15, -19,
                83, 18, 30, -13, -11, 47, 4, -9, 25, -30, 74, -4, 6, 36, -12, 63, 61, -16, 18, 40, 50));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(44, 79, -27, 64, 68, 24, 73, 35, 13, 12, 80, 28, 16, 36, -5, 41, -3,
                15, 42, 10, -9, 67, 84, 71, -27, -26, 9, -2, -31, 48, 16, 22, 42, 46, 18, 24, -21));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-1, 12, 44, -26, 85, 8, 84, 24, 46, 60, 75, -25, 9, 49, -19, 18, 37,
                -23, -11, -6, -27, 76, -31, 28, 85, -18, 68, 65, -19, 14, 53, 65, 5, 25, -9, -27, 26));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-19, 84, 42, 37, 41, -26, 14, 23, 19, 35, 16, -12, -28, -6, 60, 87,
                72, -5, 69, -9, 65, -27, 88, -31, 34, 49, 16, -15, -22, -12, 30, 22, 63, 58, 78, 59, 35));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(14, 78, 51, 70, 23, 70, 69, 65, -11, 56, 73, 66, -27, -20, 13, 49, 8,
                63, 67, -4, 68, 81, -18, 61, -21, 86, -17, 81, 51, 81, 87, 66, 27, 16, 86, 58, 15));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(9, 22, 48, 83, -7, 61, -26, 13, 63, 88, 74, 18, -6, -17, 23, -12, 6,
                -26, 85, -21, -24, 52, 67, 17, 27, 61, 24, 67, 82, 83, -30, 34, 83, 18, 65, 16, 28));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(52, 20, -18, 11, -5, -28, 86, -1, 13, -11, 45, 1, 44, 15, 25, 51, 11,
                62, 2, 86, 76, 2, 60, 11, -3, -3, 73, 69, -4, 10, -3, 41, 75, 5, -3, 9, 5));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(85, -10, -29, 17, 37, 26, -19, 8, 1, 75, -27, 35, -31, 56, 6, -28,
                -7, 65, 24, 72, -28, 29, 28, -28, 26, 50, 67, 84, -31, 20, -11, 84, 40, 65, -26, -24, -27));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(77, 55, -11, 52, 30, -4, 7, 7, 65, -19, -11, -6, 4, 46, 55, 14, 14,
                -4, 50, 5, 26, 28, 82, 72, 16, 31, 30, 3, 61, -6, -21, -4, 30, 65, 61, 14, -28));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(73, 19, 28, 78, 70, 15, -22, 83, -7, 32, -26, 70, 50, 47, 19, 66, 5,
                -13, 41, 59, 70, 31, -29, 70, 1, -29, 15, 34, 39, 32, 75, 19, 59, -8, 54, 72, 35));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-19, 37, 57, 60, -23, -28, -13, 85, 31, -25, -2, 77, 42, 6, 26, 18,
                17, 15, -28, 32, 78, 72, 11, 30, -29, 87, 61, 36, 86, -30, 18, 31, 60, -13, 74, 8, 83));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(3, 0, 56, 33, -18, 51, 58, -11, 58, 71, 33, -31, -27, 36, 78, -13,
                -16, 44, 31, -9, 55, 86, -3, 71, -21, -7, 65, 60, 36, 0, -7, 85, 64, -11, 6, 42, 85));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(33, -31, 49, 87, 84, 23, -15, 48, 8, -12, 37, -6, 26, 25, 22, -21,
                22, 81, 80, -17, -21, 4, 74, 47, 11, 80, 56, 18, -5, 26, 75, -23, 15, 38, 57, 60, 35));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-12, 57, 44, -2, -8, 15, -19, -1, 51, 2, 22, -15, -14, -24, -29, 76,
                -19, -10, 52, 48, -18, 75, -24, -18, -21, 33, 12, 14, 31, 5, 23, 5, 29, 46, 28, 66, 21));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-13, 19, 40, 44, 33, 70, -28, -26, 62, -3, 66, 82, 22, 71, -17, 59,
                -29, 75, -5, 70, 70, 45, 86, 79, 12, 63, -30, -21, 61, 80, 37, 79, 11, 14, 31, -7, 39));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-19, 80, 31, 68, 17, 4, 13, -27, 47, 74, 85, 18, 23, 42, 43, 8, -22,
                16, 38, -8, 59, 1, 68, 2, 28, 70, 49, -12, 55, 83, 11, 59, 65, 66, 33, -21, 62));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-16, 67, 80, 22, 52, 41, 79, 59, 73, 27, -10, 82, -12, -15, 52, -18,
                6, 29, 66, 61, -26, -3, -7, -1, 42, 64, 14, 59, 35, -4, 86, 27, -6, 2, -18, 77, -11));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(41, 23, -21, -23, 17, 9, 24, 65, -5, 3, 48, 70, 6, 13, 86, 64, 39,
                65, 41, 75, -4, 14, -6, 17, 51, 80, -21, -11, -7, 1, -18, 76, -18, 30, 15, 58, 66));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(72, 61, -16, 48, 42, -1, -18, 76, -30, 75, 86, -3, 19, 39, 46, 67,
                -16, 52, -17, 81, 8, 7, 18, 16, 83, 80, 57, 73, -6, 50, 65, 22, 3, 51, 82, 77, -16));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(5, 69, 10, -3, -1, 69, -2, 69, 45, 72, 70, -30, 3, 43, 22, 25, -19,
                81, -13, 87, 76, 52, 37, 87, 38, 38, 56, 36, 29, 52, 88, 54, -12, 62, 10, 53, 3));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-27, 57, -31, 9, 83, 48, -4, 25, 50, 22, 59, 50, -23, 74, 0, 46, 24,
                32, 33, 26, 79, 3, 10, -31, -22, 74, 36, 71, 24, -9, -2, 0, 74, 63, 7, 69, -17));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(50, 25, -3, -18, -8, 48, -24, 50, 3, 27, 63, 88, 41, 20, 19, 77, 86,
                1, -22, 13, 59, -5, 24, -14, -18, 54, 86, -22, -24, -30, 39, 88, 6, 87, -21, -22, -24));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-16, 5, 63, 11, 8, -30, 27, 46, 74, -30, 4, 36, -7, -11, 5, 77, 4,
                23, -11, 7, 3, 71, 58, 53, 69, -19, 77, 62, 35, -15, 80, 10, -8, 58, 9, 71, 35));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(15, 9, 51, 0, 13, 13, 56, 76, 17, 67, 59, 47, -21, 81, 26, -17, 85,
                66, 60, 78, 80, 86, 33, -28, 80, -2, -16, 53, 30, 38, 67, -17, 34, 0, -9, -7, 15));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(19, 20, 70, 72, 7, -25, 75, 13, -29, 43, -5, 20, -29, -14, 20, 83,
                24, -12, -25, -16, 18, 18, 80, 76, 26, -11, -15, 62, 80, -17, 47, 13, 7, 53, -11, 6, -16));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(16, 72, 52, 14, 72, -22, 73, 19, 33, 77, -11, 17, 44, -5, -5, 23, 70,
                26, -26, 26, 69, 27, 20, -20, 56, 49, 32, 73, 76, 27, 12, 69, 7, -4, 4, 55, -10));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(40, 84, 47, 67, 69, -20, -29, 62, -19, 1, -21, 80, 36, 73, -5, -4,
                71, 49, -2, 48, 8, 77, 81, 51, -17, 14, 41, 49, 22, 28, 34, -2, 71, 60, 4, 54, 25));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(1, 66, 9, -29, -17, 37, 82, 81, 19, 81, 17, -11, 22, 59, 75, -27, 20,
                0, 23, -27, 39, 74, 7, 75, -17, -5, -21, 67, 31, 24, 66, 40, 14, 15, 26, -17, 2));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(2, 59, -25, -5, 10, 87, 42, 87, 36, -19, 54, 45, 40, 73, 12, 68, 80,
                13, -29, 14, 75, 9, 43, -26, -1, 4, 2, 28, 51, 36, -4, -29, 46, 12, 70, 24, 22));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(83, 5, 61, 53, 18, 28, -25, 43, 45, 85, 70, 71, 61, -2, -26, 67, 57,
                82, 43, 56, -1, 46, 9, -7, 67, 31, -15, 46, -29, 16, 7, 40, 55, -17, -20, 83, -29));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(13, 13, 69, 10, 48, 27, -21, -13, -26, 67, -6, 9, -23, 7, 14, -14,
                -20, 4, 2, 82, -10, 28, 76, -16, -11, 59, -3, 75, -15, 4, 29, 57, 80, 12, 53, 12, 19));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(52, 85, -31, 7, 22, -17, 63, 25, 36, -11, -3, 48, 58, -23, 71, 32,
                21, 83, -23, 9, 32, 32, -15, 63, 27, 67, 72, 77, -25, 2, 21, 48, 13, 74, 19, 84, 0));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(51, 16, 49, 40, 16, 84, -1, 27, 88, -13, 87, 82, 45, 47, 33, -3, -11,
                -9, 88, 60, 60, -23, 85, 57, -16, 19, 52, 87, -20, 4, 85, -25, 86, 41, 8, 81, 69));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(16, -5, 16, 41, -25, 59, 85, 51, 66, 74, 83, 88, 42, 67, 44, 22, -25,
                43, 78, 74, 86, 9, 49, -1, 84, 67, -5, 48, 85, -31, 22, 9, 27, 59, 37, 21, -25));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-20, -24, 79, 78, -10, 15, 35, -29, 14, 35, 17, 78, -25, 35, -22, 30,
                10, 17, -25, 77, 58, 73, 24, -8, 6, 35, -2, 52, 76, 44, 62, 71, 82, -5, 75, 8, 41));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(80, 7, -30, 24, 60, 80, 35, 0, 36, 43, 41, 62, 77, 63, 3, -27, 86,
                -10, -1, 74, -20, 61, -11, 70, -31, 40, 15, 85, 64, 1, 61, 22, 7, -11, 33, 47, 24));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(3, 38, 77, -1, 30, 56, 35, -26, 68, 52, 63, 20, 78, 79, 58, 62, -27,
                -23, -22, 18, 32, 44, 51, -3, 50, 39, -22, 1, -11, 19, -22, 56, -22, 44, -29, 76, 76));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-11, 41, 14, 43, 29, -11, 16, 3, 52, 26, -1, 21, 12, -10, 11, 34, -5,
                52, 34, 80, 74, 51, 63, 53, -28, 24, -21, -12, 87, -19, 49, 17, 21, -27, 60, -31, 38));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-22, 88, 80, -24, -24, -10, -9, -13, -7, 39, 65, -8, 14, 46, 29, 41,
                -19, -25, 50, -23, 51, 69, 2, 19, 75, -19, 66, -9, -26, 72, 11, 35, 50, 50, 26, -20, 22));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(14, -25, -31, 63, 83, -3, 35, 78, 61, 20, -21, 45, -23, 1, -21, 13,
                -11, 2, 12, -13, -9, 68, -6, 81, 86, -3, -2, 88, 56, 77, 62, 62, 55, 55, 88, 22, 83));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(-17, 10, 71, 47, 19, 13, -21, -24, -16, -3, -19, 49, 2, 80, 13, 69,
                -29, 21, 53, 59, 6, 78, 62, 55, 21, 64, 76, -30, -20, 84, 53, 19, 56, 50, 84, 83, 50));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(47, -28, 88, 61, 20, 40, 64, 43, -30, 26, 51, 17, 51, 9, -25, 40, -3,
                -27, 52, -1, -31, 46, 54, 60, 64, 71, 7, 20, -2, 13, 24, -4, 30, -6, 54, 5, -10));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(51, 55, 65, 76, -14, 54, -18, -10, 20, 36, 37, 81, 63, -13, 49, -31,
                8, -14, -24, 72, 46, 7, 34, 20, 31, 21, 65, 6, 70, 76, 12, 72, 64, 7, 73, 6, 14));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(43, 48, 6, -21, 65, 58, 18, 63, -2, -4, 51, -30, 53, 82, 83, -23, 29,
                11, 74, -26, 27, -11, 1, 56, 51, 83, -24, -10, 62, 63, 66, 43, 82, -5, -25, 81, 15));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(64, 59, 59, 7, -15, 15, 69, 68, 33, 73, 72, -25, 72, 47, 25, 24, 32,
                27, 3, -7, 65, -22, 25, 77, 18, -8, -4, 0, -29, 21, -24, 57, 70, 75, -13, 71, 32));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(8, -27, 24, 82, 38, 46, 86, 79, 29, 59, -15, 64, 79, 24, 23, 23, 66,
                68, 29, 29, -25, 77, 18, 71, 77, 27, -19, 18, -23, 26, 39, -8, 37, 39, 72, 2, 4));
        PASSWORD_TRANSFORMER_MAP.add(Arrays.asList(10, 74, 8, 83, 40, 1, 50, -14, 56, -12, -23, -4, -26, 53, 57, 74, 78,
                48, -28, 9, 22, -17, 5, 69, 16, -15, 88, 35, 44, -25, -14, 10, -18, 10, 72, 43, 2));

        TRANSFORMATION_ROW_MAX_DIGITS = String.format("%d", PASSWORD_TRANSFORMER_MAP.size()).length() + 1;
    }

    /**
     * Encode a properties value so that it can be safed "relativly save". An
     * already encoded password will return unchanged.
     * <br>
     * The encoded password can be decoded with the decode method.<br>
     * <br>The password must not start with ??.<br>
     * <br>This method is only for encoding the value, not for saving it.<br>
     *
     * @param decValue the value to encode
     * @return An encoded version of the password.
     */
    public static String encode(String decValue) {
        if(decValue != null){
            
        
        StringBuilder builder = new StringBuilder();
        if (!decValue.startsWith(ENCRYPTION_MARKER)) {
            builder.append(ENCRYPTION_MARKER);
            String rawPassword;
            if (decValue.length() < ANTI_TRIMMER.length()) {
                rawPassword = ANTI_TRIMMER.substring(0, ANTI_TRIMMER.length() - decValue.length()) + decValue;
            } else {
                rawPassword = decValue;
            }
            int row = getEncryptionRow(rawPassword);
            String formattedRow = String.format("%0" + TRANSFORMATION_ROW_MAX_DIGITS + "d", row);
            builder.append(formattedRow);
            for (int i = 0; i < rawPassword.length(); i++) {
                int c = rawPassword.charAt(i) + getOffset(row, i);
                String h = String.format("%04x", c);
                builder.append(h);
            }
        } else {
            builder.append(decValue);
        }
        return builder.toString();
        }else{
            return null;
        }
    }

    /**
     * Decodes a value so that it can be used by the SDK. An already decoded
     * password will return unchanged.
     * <br>
     * The decoded value can be encoded with the encode method.
     *
     * @param encValue the value to decode
     * @return the decoded version of the value.
     */
    public static String decode(String encValue) {
        if (encValue != null) {

            StringBuilder builder = new StringBuilder();
            if (encValue.startsWith(ENCRYPTION_MARKER)) {
                String encryptedPassword = encValue.substring(ENCRYPTION_MARKER.length() + TRANSFORMATION_ROW_MAX_DIGITS);
                int row = Integer.valueOf(encValue.substring(ENCRYPTION_MARKER.length(),
                        ENCRYPTION_MARKER.length() + TRANSFORMATION_ROW_MAX_DIGITS));
                int current = 0;
                for (int i = 0; i < encryptedPassword.length(); i += 4) {
                    String h = encryptedPassword.substring(i, i + 4);
                    int c = Integer.parseInt(h, 16) - getOffset(row, current++);
                    if (builder.length() > 0 || c != 32) {
                        builder.append((char) c);
                    }
                }
            } else {
                builder.append(encValue);
            }
        return builder.toString();
        }
        else{
            return null;
        }
    }

    /**
     * This method decodes every property value, if it is encoded.
     * @param properties the properties to decode
     * @return the decoded properties
     */
    public static Properties decode(Properties properties) {
        Properties newProps = new Properties();
        properties.keySet().forEach(key -> newProps.put(key, decode((String) properties.get(key))));
        return newProps;
    }

    private static int getEncryptionRow(String password) {
        int c = 5 * password.length() + 13;
        for (int i = 0; i < password.length(); i++) {
            c = c + 11 * password.charAt(i);
        }
        c = c % PASSWORD_TRANSFORMER_MAP.size();
        return c;
    }

    private static int getOffset(int row, int current) {
        List<Integer> offsets
                = row < 0 || row >= PASSWORD_TRANSFORMER_MAP.size() ? null : PASSWORD_TRANSFORMER_MAP.get(row);
        int offset;
        if (offsets == null || current < 0) {
            offset = 0;
        } else {
            offset = offsets.get(current % offsets.size());
        }
        return offset;
    }

}
