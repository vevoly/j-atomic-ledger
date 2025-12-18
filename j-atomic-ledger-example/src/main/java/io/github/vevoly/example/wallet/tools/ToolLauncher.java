package io.github.vevoly.example.wallet.tools;

import io.github.vevoly.example.wallet.domain.TradeCommand;
import io.github.vevoly.ledger.core.tools.SnapshotViewer;
import io.github.vevoly.ledger.core.tools.WalViewer;

/**
 * <h3>开发调试专用工具启动器 (Dev & Debug Tool Launcher)</h3>
 *
 * <p>
 * 此类提供了一个统一的 main 方法入口，用于在 IDE 开发环境中方便地运行和调试核心工具 (如 WalViewer, SnapshotViewer)。
 * 它利用了 example 模块的完整 Classpath，解决了工具类无法直接访问业务类的问题。
 * </p>
 *
 * <p><b>注意：</b>此类仅供开发调试使用，不应在生产环境直接调用。</p>
 *
 * <hr>
 * <span style="color: gray; font-size: 0.9em;">
 * <b>Dev & Debug Tool Launcher.</b><br>
 * Provides a unified main entry point for running and debugging core tools (e.g., WalViewer) within the IDE.<br>
 * It leverages the full classpath of the 'example' module to resolve business classes.<br>
 * <b>Note:</b> This class is for development and debugging purposes only.
 * </span>
 *
 * @author vevoly
 */
public class ToolLauncher {

    public static void main(String[] args) throws Exception {
        String basePath = "./data/ledger-demo/wallet-core/";
        String partition = "wallet-core-p0";
        // 改成 "wal" 或 "snapshot" / change to "wal" or "snapshot"
        String toolToRun = "snapshot";

        switch (toolToRun) {
            case "wal":
                viewWal(basePath + partition + "/wal");
                break;
            case "snapshot":
                viewSnapshot(basePath + partition + "/snapshot/snapshot.dat");
                break;
            default:
                System.err.println("Unknown tool: " + toolToRun);
        }
    }

    private static void viewWal(String path) throws Exception {
        System.out.println("\n\n=============== WAL VIEWER ===============\n");
        // 调用并传入业务 Command Class / Call and pass the business Command Class
        WalViewer.main(new String[]{path, TradeCommand.class.getName()});
    }

    private static void viewSnapshot(String path) throws Exception {
        System.out.println("\n\n=========== SNAPSHOT VIEWER ===========\n");
        // SnapshotViewer 的 Classpath 也需要包含业务类 / SnapshotViewer also needs business classes on its classpath
        SnapshotViewer.main(new String[]{path});
    }
}
