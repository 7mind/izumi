package spike; public final class SuiteA extends Spec { public static final int REVISION = 4; public void body(int index) { Application.audit("BODY spike.SuiteA test" + index); } }
