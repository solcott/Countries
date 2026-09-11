// ComposeGraphSaverTest builds the real graph, which reaches :ui and therefore drags skiko into
// the browser test bundle -- ~15 MB of skiko.wasm plus the Compose runtime. On a CI runner,
// headless Chrome needs well over karma's 30s browserNoActivityTimeout default just to download
// and start that bundle before the first test reports, so it disconnects with
// "no message in 30000 ms". The task then fails claiming it "did not discover any tests", which
// points nowhere near the real cause. Give the browser room.
//
// This is the heaviest browser test bundle in the build; :presenter and :ui load under the default
// and so carry no such file. Add the same snippet to them if they start disconnecting on CI.
config.set({
  browserNoActivityTimeout: 300000,
  browserDisconnectTimeout: 60000,
  pingTimeout: 60000,
});
