/**
 * A helper class for the Simple TF/IDF search proof of concept.
 *
 * The purpose of this program was to learn, develop and test the
 * TF/IDF search algorithm. It does not play an active part of the
 * overall project. Two data processing jobs and the API are based
 * off of this exercise. It stays here for reference.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */
package cis5550.tools;

public class TFIDFSearchResult {
    private TFIDFDocument document;
    private double score;

    public TFIDFSearchResult(TFIDFDocument document, double score) {
        this.document = document;
        this.score = score;
    }

    public TFIDFDocument getDocument() {
        return document;
    }

    public double getScore() {
        return score;
    }
}