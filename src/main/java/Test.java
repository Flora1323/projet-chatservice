public class Test {
    public static void main(String[] args) throws Exception {
        Message.insertMessage(1, 2, "Hello");
        Message.getMessages(2);
        Message.saveFile(1, "C:/files/test.txt");
    }
}