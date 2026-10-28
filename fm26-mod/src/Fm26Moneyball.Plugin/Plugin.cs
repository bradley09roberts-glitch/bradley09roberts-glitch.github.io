using System;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading;
using BepInEx;
using BepInEx.Configuration;
using HarmonyLib;
using UnityEngine;

namespace Fm26Moneyball.Plugin
{
    [BepInPlugin(Guid, "FM26 Moneyball", "0.1.0")]
    public sealed class Plugin : BaseUnityPlugin
    {
        public const string Guid = "com.bradley.fm26moneyball";

        ConfigEntry<bool> _discover;
        ConfigEntry<float> _delay;
        ConfigEntry<string> _keywords;
        ConfigEntry<bool> _traceOn;
        ConfigEntry<string> _targets;
        ConfigEntry<int> _maxHits;

        string _dir;
        readonly object _traceLock = new object();

        void Awake()
        {
            _discover = Config.Bind("Discovery", "RunOnStart", true,
                "Scan the game's code for transfer, contract and wage classes and write discovery.txt.");
            _delay = Config.Bind("Discovery", "DelaySeconds", 20f,
                "Wait this long after launch before scanning, so the game has loaded its assemblies.");
            _keywords = Config.Bind("Discovery", "Keywords",
                "transfer,negotiat,contract,wage,offer,bid,agent,clause,budget,bonus,instal",
                "Comma-separated, case-insensitive words matched against type and method names.");
            _traceOn = Config.Bind("Trace", "Enabled", false,
                "Log every call to the methods listed in Targets. Turn on after you have chosen targets from discovery.txt.");
            _targets = Config.Bind("Trace", "Targets", "",
                "Semicolon-separated Namespace.Type::Method entries. Every overload is traced.");
            _maxHits = Config.Bind("Trace", "MaxHitsPerMethod", 200,
                "Stop logging a method after this many calls so the file stays readable.");

            _dir = Path.Combine(Paths.BepInExRootPath, "fm26-moneyball");
            Directory.CreateDirectory(_dir);

            // Unity API calls must happen here on the main thread; the scan itself runs in the background.
            var header = "Unity " + Application.unityVersion + " | " + Application.productName + " " + Application.version +
                         " | " + DateTime.Now.ToString("yyyy-MM-dd HH:mm");
            Logger.LogInfo("FM26 Moneyball loaded. " + header);

            if (!_discover.Value && !_traceOn.Value) return;
            var delay = Math.Max(0f, _delay.Value);
            new Thread(() => { Thread.Sleep((int)(delay * 1000)); Run(header); }) { IsBackground = true, Name = "fm26-moneyball" }.Start();
        }

        void Run(string header)
        {
            try
            {
                var asms = AppDomain.CurrentDomain.GetAssemblies();
                var game = asms.Where(Discovery.IsGameAssembly).ToArray();

                if (_discover.Value)
                {
                    var kw = _keywords.Value.Split(',');
                    var lines = Discovery.Scan(asms, kw);
                    var sb = new StringBuilder();
                    sb.AppendLine("# FM26 Moneyball discovery | " + header);
                    sb.AppendLine("# Keywords: " + _keywords.Value);
                    sb.AppendLine("# Game assemblies: " + string.Join(", ", game.Select(a => a.GetName().Name)));
                    sb.AppendLine();
                    foreach (var l in lines) sb.AppendLine(l);
                    File.WriteAllText(Path.Combine(_dir, "discovery.txt"), sb.ToString());
                    Logger.LogInfo("Discovery wrote " + lines.Count + " lines to " + Path.Combine(_dir, "discovery.txt"));
                }

                if (_traceOn.Value && _targets.Value.Trim().Length > 0)
                {
                    Tracer.MaxHitsPerMethod = _maxHits.Value;
                    var tracePath = Path.Combine(_dir, "trace.log");
                    var report = Tracer.Apply(new Harmony(Guid), asms, _targets.Value.Split(';'), line =>
                    {
                        lock (_traceLock) File.AppendAllText(tracePath, line + Environment.NewLine);
                    });
                    foreach (var r in report) Logger.LogInfo("Trace " + r);
                }
            }
            catch (Exception e)
            {
                Logger.LogError("FM26 Moneyball failed: " + e);
            }
        }
    }
}
