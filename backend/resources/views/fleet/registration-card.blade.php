<aside class="panel fleet-register-card">
    <img src="{{ asset('images/'.$image.'.webp') }}" alt="" width="850" height="566">
    <div>
        <h2>{{ $heading }}</h2>
        <p>{{ $description }}</p>
        <a class="button primary" href="{{ route($registerRoute) }}">{{ $buttonLabel }}</a>
    </div>
</aside>
