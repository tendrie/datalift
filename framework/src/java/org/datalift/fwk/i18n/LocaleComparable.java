package org.datalift.fwk.i18n;


import java.text.Collator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A wrapper that allows sorting objects according to the
 * {@link Collator rules} specific to each human language and taking
 * care of correctly ordering data with embedded integer values (by
 * separating string parts from numeric parts).
 * <p>
 * Hence</p>
 * <ul>
 *  <li>&quot;5th century&quot; &lt; &quot;19th century&quot;</li>
 *  <li>&quot;Photo-83.jpg&quot; &lt; &quot;Photo-138.jpg&quot;</li>
 * </ul>
 */
@SuppressWarnings("unchecked")
public class LocaleComparable<T> implements Comparable<LocaleComparable<T>>
{
    /** A regular expression to match integer values within strings. */
    private final static Pattern NUM_ELT_PATTERN = Pattern.compile("[0-9]+");

    public final String key;
    public final T data;
    private final Comparable[] elts;

    public LocaleComparable(String key, T data) {
        this(key, data, (Locale)null);
    }

    public LocaleComparable(String key, T data, Locale locale) {
        this(key, data, (locale != null)? Collator.getInstance(locale):
                                          Collator.getInstance());
    }

    public LocaleComparable(String key, T data, Collator collator) {
        if (key == null) {
            throw new IllegalArgumentException("key");
        }
        if (collator == null) {
            throw new IllegalArgumentException("collator");
        }
        this.key  = key;
        this.data = data;
        this.elts = this.parse(key, collator);
    }

    @Override
    public int compareTo(LocaleComparable<T> o) {
        if (o == null) {
            return 1;       // Any value (this) is larger than null.
        }
        int result = 0;
        // Compare common parts of both values, one by one,
        // until a difference is found.
        int max = Math.min(this.elts.length, o.elts.length);
        int i = 0;
        while ((result == 0) && (i < max)) {
            if (this.elts[i].getClass() == o.elts[i].getClass()) {
                // Same type of part. => Compare them.
                result = this.elts[i].compareTo(o.elts[i]);
            }
            else {
                // Not the same type. => Integers come before strings.
                result = (this.elts[i] instanceof Integer)? -1: 1;
            }
            i++;
        }
        if (result == 0) {
            // All parts present in both values are equals.
            // => The value with remaining parts is larger.
            result = this.elts.length - o.elts.length;
        }
        return result;
     }


    private Comparable<?>[] parse(String s, Collator c) {
        List<Comparable<?>> l = new LinkedList<Comparable<?>>();
        // Extract comparables, separating numeric value from strings.
        Matcher m = NUM_ELT_PATTERN.matcher(s);
        int i = 0;
        while (m.find()) {
            // Numeric value found.
            int j = m.start();
            if (i != j) {
                // String prefix.
                l.add(c.getCollationKey(s.substring(i, j)));
            }
            // Numeric value
            i = m.end();
            l.add(Integer.valueOf(s.substring(j, i)));
        }
        int n = s.length();
        if (i != n) {
            l.add(c.getCollationKey(s.substring(i, n)));
        }
        return l.toArray(new Comparable[l.size()]);
    }
}