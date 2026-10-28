using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using HarmonyLib;

namespace Fm26Moneyball
{
    /// <summary>
    /// Logs every call to the methods named in the config, with arguments and result. Run a negotiation in game
    /// and the log shows which method carries the fee and wage, and what they look like.
    /// </summary>
    public static class Tracer
    {
        static Action<string> _sink = s => { };
        static readonly Dictionary<MethodBase, int> Hits = new Dictionary<MethodBase, int>();
        public static int MaxHitsPerMethod { get; set; } = 200;

        /// <summary>Targets are written "Namespace.Type::Method"; every overload is patched.</summary>
        public static List<string> Apply(Harmony harmony, IEnumerable<Assembly> assemblies, IEnumerable<string> targets, Action<string> sink)
        {
            _sink = sink ?? (s => { });
            var report = new List<string>();
            var asms = assemblies.Where(Discovery.IsGameAssembly).ToArray();
            var postVoid = new HarmonyMethod(typeof(Tracer).GetMethod(nameof(PostfixVoid), BindingFlags.Public | BindingFlags.Static));
            var postResult = new HarmonyMethod(typeof(Tracer).GetMethod(nameof(PostfixResult), BindingFlags.Public | BindingFlags.Static));

            foreach (var raw in targets.Select(t => t.Trim()).Where(t => t.Length > 0))
            {
                var parts = raw.Split(new[] { "::" }, StringSplitOptions.None);
                if (parts.Length != 2) { report.Add("SKIP " + raw + " (expected Type::Method)"); continue; }

                var type = asms.Select(a => a.GetType(parts[0], false)).FirstOrDefault(t => t != null);
                if (type == null) { report.Add("MISS " + raw + " (type not found)"); continue; }

                var methods = type.GetMethods(BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.DeclaredOnly)
                                  .Where(m => m.Name == parts[1] && !m.IsAbstract && !m.ContainsGenericParameters).ToArray();
                if (methods.Length == 0) { report.Add("MISS " + raw + " (method not found)"); continue; }

                foreach (var m in methods)
                {
                    try
                    {
                        harmony.Patch(m, postfix: m.ReturnType == typeof(void) ? postVoid : postResult);
                        report.Add("OK   " + type.FullName + "::" + m.Name + "(" + m.GetParameters().Length + " args)");
                    }
                    catch (Exception e) { report.Add("FAIL " + raw + ": " + e.GetType().Name + " " + e.Message); }
                }
            }
            return report;
        }

        public static void PostfixVoid(object __instance, object[] __args, MethodBase __originalMethod) =>
            Log(__originalMethod, __instance, __args, null, false);

        public static void PostfixResult(object __instance, object[] __args, MethodBase __originalMethod, object __result) =>
            Log(__originalMethod, __instance, __args, __result, true);

        static void Log(MethodBase m, object self, object[] args, object result, bool hasResult)
        {
            try
            {
                lock (Hits)
                {
                    int n; Hits.TryGetValue(m, out n);
                    if (n >= MaxHitsPerMethod) return;
                    Hits[m] = n + 1;
                }
                var line = "[" + DateTime.Now.ToString("HH:mm:ss.fff") + "] " + m.DeclaringType.FullName + "::" + m.Name + "(" +
                           string.Join(", ", (args ?? new object[0]).Select(a => ObjectDumper.Describe(a))) + ")" +
                           (m.IsStatic ? "" : "  this=" + ObjectDumper.Describe(self)) +
                           (hasResult ? "  =>  " + ObjectDumper.Describe(result) : "");
                _sink(line.Length > 1500 ? line.Substring(0, 1500) + "…" : line);
            }
            catch { /* logging must never break the game */ }
        }
    }
}
