package cis5550.tools;


import cis5550.tools.Helpers;

class Tester {

    public static void main(String args[]) {
        Helpers.loadEnglishWords(args[0]);

        String myText = "This category is not shown on its member pages";

        System.out.println("Raw: " + myText);
        System.out.println("Get: " + String.join(" ", Helpers.getWords(myText)));

        System.out.println("Size: " + Helpers.englishWords.size());

        for(int i = 0; i < 10; i ++) {
            System.out.println("random: " + Helpers.randomPageRank());
        }
    }
}