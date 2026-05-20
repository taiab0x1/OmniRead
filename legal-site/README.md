# OmniRead Legal Site

Static public pages for Play Store and AdMob review.

Target host:

```text
https://omnireadapp.<domain>
```

## Files

- `public/index.html` - simple support landing page
- `public/privacy/index.html` - privacy policy
- `public/delete-account/index.html` - account deletion instructions
- `public/support/index.html` - support/contact page
- `public/app-ads.txt` - AdMob app-ads.txt placeholder
- `nginx/omnireadapp.conf.template` - Nginx server block template

## Deploy To Current Server

After DNS is created and SSH/panel access is available:

```bash
sudo mkdir -p /var/www/omnireadapp
sudo rsync -av public/ /var/www/omnireadapp/
sudo cp nginx/omnireadapp.conf.template /etc/nginx/sites-available/omnireadapp
sudo sed -i 's/omnireadapp.example.com/omnireadapp.<domain>/g' /etc/nginx/sites-available/omnireadapp
sudo ln -s /etc/nginx/sites-available/omnireadapp /etc/nginx/sites-enabled/omnireadapp
sudo nginx -t
sudo systemctl reload nginx
```

Then issue TLS for the hostname, for example:

```bash
sudo certbot --nginx -d omnireadapp.<domain>
```

## Verify

```bash
curl -I https://omnireadapp.<domain>/privacy/
curl -I https://omnireadapp.<domain>/delete-account/
curl -I https://omnireadapp.<domain>/support/
curl https://omnireadapp.<domain>/app-ads.txt
```
