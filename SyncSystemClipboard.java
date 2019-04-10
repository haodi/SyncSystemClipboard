package learning;

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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author lihaodi
 */
public class SyncSystemClipboard implements ClipboardOwner {
    private static String IP;
    private final static int PORT = 4396;

    private Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    public static void main(String[] args) {
        if (args.length > 0) {
            IP = args[0];
        } else {
            throw new IllegalArgumentException("Params error IP address cannot be null");
        }

        SyncSystemClipboard syncSystemClipboard = new SyncSystemClipboard();

        System.out.println("Initializing receive client ...");
        syncSystemClipboard.initClient();
        System.out.println("Initializing receive client success.");

        System.out.println("Initializing socket server on port " + PORT);
        syncSystemClipboard.initSocketServer();
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
                StringSelection stringSelection = new StringSelection(text);
                clipboard.setContents(stringSelection, this);
                System.out.println("Copy Text : " + text);

                this.sync(text);
            } catch (UnsupportedFlavorException | IOException e) {
                e.printStackTrace();
            }
        }

        if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            try {
                Image screenshot = (Image) clipboard.getData(DataFlavor.imageFlavor);
                ImageTransferable imageTransferable = new ImageTransferable(screenshot);
                clipboard.setContents(imageTransferable, this);
                System.out.println("Copy Screenshot : " + imageTransferable);

                this.sync(imageTransferable);
            } catch (UnsupportedFlavorException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sync(Object o) {
        try (Socket socket = new Socket(IP, PORT)) {
            if (o != null) {
                OutputStream outputStream = socket.getOutputStream();
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

                if (o instanceof String) {
                    dataOutputStream.write(((String) o).getBytes());
                }

                if (o instanceof ImageTransferable) {
                    dataOutputStream.write(((ImageTransferable) o).getBytes());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initClient() {
        clipboard.setContents(clipboard.getContents(null), this);
        new JFrame().setVisible(true);
    }

    private void initSocketServer() {
        try (ServerSocket serverSocket = new ServerSocket(4396)) {
            try (Socket socket = serverSocket.accept()) {
                byte[] bytes = this.getBytes(socket.getInputStream());
                this.pasteToClipboard(bytes);
            }
        } catch (IOException e) {
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

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void pasteToClipboard(byte[] bytes) {
        try {
            Transferable transferable = null;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                transferable = new ImageTransferable(image);

                System.out.println("Receive Screenshot : " + transferable);
            } else {
                transferable = new StringSelection(new String(bytes));

                System.out.println("Receive Text : " + transferable.getTransferData(DataFlavor.stringFlavor));
            }
            this.clipboard.setContents(transferable, this);
        } catch (IOException | UnsupportedFlavorException e) {
            e.printStackTrace();
        }
    }
}

class ImageTransferable implements Transferable {
    private Image image;

    ImageTransferable(Image image) {
        this.image = image;
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toByteArray();
    }
}
