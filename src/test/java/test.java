import java.util.concurrent.FutureTask;

public class test {
    static ThreadLocal<Integer>int1 = new ThreadLocal<>();
    static ThreadLocal<String>s2 = new ThreadLocal<>();
    public static void main(String[] args) {
        int1.set(10);
        s2.set("123");
        for(int i = 0;i < 3;i++){
            new Thread(new Runnable() {
                            @Override
                            public void run() {
                                System.out.println(int1.get() + " " + s2.get());
                            }
                        }, "Thread-" + i).start();
        }
    }
}
