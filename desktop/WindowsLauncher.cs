using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class WindowsLauncher
{
    [STAThread]
    private static int Main()
    {
        string root = AppDomain.CurrentDomain.BaseDirectory;
        string application = Path.Combine(root, "Qwen-voice-Windows", "Qwen-voice.exe");
        string runtime = Path.Combine(root, "Qwen-voice-Windows", "_internal");
        try
        {
            if (!File.Exists(application) || !Directory.Exists(runtime))
                throw new FileNotFoundException("找不到程序文件，请保留完整的 Qwen-voice-Windows 文件夹。");

            var start = new ProcessStartInfo(application);
            start.WorkingDirectory = runtime;
            start.UseShellExecute = false;
            using (Process process = Process.Start(start))
            {
                if (process == null) throw new InvalidOperationException("无法启动 Qwen-voice。");
                if (!String.IsNullOrEmpty(Environment.GetEnvironmentVariable("QWEN_EXEC_SELFTEST")))
                {
                    process.WaitForExit();
                    return process.ExitCode;
                }
            }
            return 0;
        }
        catch (Exception error)
        {
            MessageBox.Show(error.Message, "Qwen-voice", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }
}
