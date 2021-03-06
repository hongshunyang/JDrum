package at.rovo.drum.util;

import java.io.Serializable;

/**
 * Simple test class used to test de/serialization.
 *
 * @author Roman Vottner
 */
public class TestClass implements Serializable {

    private static final long serialVersionUID = -1887112715340505635L;
    private String msg;

    TestClass(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return this.msg;
    }

    @Override
    public int hashCode() {
        return 31 * msg.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TestClass) {
            TestClass other = (TestClass) o;
            return other.toString().equals(this.toString());
        }
        return false;
    }
}