package learning;

import org.apache.commons.codec.digest.DigestUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author lihaodi
 */
public class SyncSystemClipboard implements ClipboardOwner {
    private static String IP;
    private final static int PORT = 4396;
    private final static String MAC = "Mac";

    private Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private Object lastClipboard = null;

    private static String currentOSName = "";

    public static void main(String[] args) {
        if (args.length > 0) {
            IP = args[0];
        } else {
            throw new IllegalArgumentException("Params error IP address cannot be null");
        }

        SyncSystemClipboard syncSystemClipboard = new SyncSystemClipboard();

        currentOSName = System.getProperty("os.name");

        System.out.println("Current OS :" + currentOSName);

        // Mac系统通过定时的方式同步剪贴板（ClipboardOwner再Mac系统下不工作）
        if (isMacOs()) {
            System.out.println("Initializing monitor clipboard timer...");

            syncSystemClipboard.initTimer();
        } else {
            System.out.println("Initializing receive client ...");

            new Thread(() -> syncSystemClipboard.initClient()).start();
        }

        new Thread(() -> syncSystemClipboard.initSocketServer()).start();

        System.out.println("Initializing socket server on port " + PORT);
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            try {
                String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                System.out.println("Copy Text : " + text);

                this.sync(text.getBytes(Charset.forName("UTF-8")));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            try {
                Image screenshot = (Image) clipboard.getData(DataFlavor.imageFlavor);
                ImageTransferable imageTransferable = new ImageTransferable(screenshot);
                System.out.println("Copy Screenshot(md5:" + imageTransferable.getMd5() + ") : " + imageTransferable);

                this.sync(imageTransferable.getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sync(byte[] bytes) throws IOException {
        try (Socket socket = new Socket(IP, PORT)) {
            if (bytes != null && bytes.length > 0) {
                OutputStream outputStream = socket.getOutputStream();
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                dataOutputStream.write(bytes);
            }
        }
    }

    private void initClient() {
        clipboard.setContents(clipboard.getContents(null), this);
        new JFrame().setVisible(true);
    }

    private void initSocketServer() {
        try (ServerSocket serverSocket = new ServerSocket(4396)) {
            while (true) {
                try (Socket socket = serverSocket.accept()) {
                    byte[] bytes = this.getBytes(socket.getInputStream());
                    this.pasteToClipboard(bytes);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] getBytes(InputStream inputStream) {
        try {
            int length;
            byte[] bytes = new byte[1024];
            DataInputStream in = new DataInputStream(inputStream);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            while ((length = in.read(bytes, 0, bytes.length)) != -1) {
                byteArrayOutputStream.write(bytes, 0, length);
            }
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void pasteToClipboard(byte[] bytes) {
        try {
            Transferable transferable;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                transferable = new ImageTransferable(image);

                System.out.println("Receive Screenshot : " + transferable);
            } else {
                transferable = new StringSelection(new String(bytes, Charset.forName("UTF-8")));

                System.out.println("Receive Text : " + transferable.getTransferData(DataFlavor.stringFlavor));
            }

            this.clipboard.setContents(transferable, this);

            if (transferable instanceof StringSelection) {
                lastClipboard = transferable.getTransferData(DataFlavor.stringFlavor);
            }

            if (transferable instanceof ImageTransferable) {
                lastClipboard = ((ImageTransferable) transferable).getMd5();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initTimer() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                Transferable transferable = this.clipboard.getContents(DataFlavor.stringFlavor);

                if (transferable == null) {
                    transferable = this.clipboard.getContents(DataFlavor.imageFlavor);
                }

                if (transferable != null) {
                    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)
                            && !transferable.getTransferData(DataFlavor.stringFlavor).equals(lastClipboard)) {
                        lastClipboard = transferable.getTransferData(DataFlavor.stringFlavor);

                        this.lostOwnership(this.clipboard, transferable);
                        return;
                    }

                    if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        Image screenshot = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                        ImageTransferable imageTransferable = new ImageTransferable(screenshot);

                        if (!imageTransferable.getMd5().equals(lastClipboard)) {
                            lastClipboard = imageTransferable.getMd5();

                            this.lostOwnership(this.clipboard, transferable);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private static boolean isMacOs() {
        return System.getProperty("os.name").startsWith(MAC);
    }
}

class ImageTransferable implements Transferable {
    private Image image;
    private String md5;

    ImageTransferable(Image image) {
        this.image = this.getImage(image);
        this.md5 = DigestUtils.md5Hex(this.getBytes());
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{DataFlavor.imageFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor == DataFlavor.imageFlavor;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        return image;
    }

    byte[] getBytes() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(((BufferedImage) image), "jpg", byteArrayOutputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toByteArray();
    }

    public BufferedImage getImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }

        Lock lock = new ReentrantLock();
        Condition size = lock.newCondition(), data = lock.newCondition();
        ImageObserver o = (img, infoflags, x, y, width, height) -> {
            lock.lock();
            try {
                if ((infoflags & ImageObserver.ALLBITS) != 0) {
                    size.signal();
                    data.signal();
                    return false;
                }
                if ((infoflags & (ImageObserver.WIDTH | ImageObserver.HEIGHT)) != 0) {
                    size.signal();
                }
                return true;
            } finally {
                lock.unlock();
            }
        };
        BufferedImage bi;
        lock.lock();
        try {
            int width, height = 0;
            while ((width = image.getWidth(o)) < 0 || (height = image.getHeight(o)) < 0)
                size.awaitUninterruptibly();
            bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            try {
                g.setBackground(new Color(0, true));
                g.clearRect(0, 0, width, height);
                while (!g.drawImage(image, 0, 0, o)) data.awaitUninterruptibly();
            } finally {
                g.dispose();
            }
        } finally {
            lock.unlock();
        }
        return bi;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }
}
