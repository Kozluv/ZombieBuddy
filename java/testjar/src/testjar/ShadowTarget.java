package testjar;

// package-private type/field; patch access via VarHandle + MethodHandles.privateLookupIn factory
class ShadowTarget {
    private int privateField = 69;

    private int privateMethod() {
        return 42;
    }
}
