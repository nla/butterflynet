$(document).ready(function() {
    $("abbr.timeago").timeago();

    var captureList = $("#captureList");
    if (captureList.length > 0) {
        var events = new EventSource("events");
        events.addEventListener("update", function(e) {
            var data = JSON.parse(e.data);
            captureList.html(data.captureList);
        }, false);
    }
});

